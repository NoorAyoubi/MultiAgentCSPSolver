import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent implementing FC-CBJ (Forward Checking with Conflict-Directed Backjumping)
 * for the Distributed N-Queens problem.
 */
public class Agent implements Runnable {

    private final int id;
    private final int n;
    private final Mailer mailer;

    // Neighbors collection (explicitly represented for neighbor-based communication)
    private final List<Integer> neighbors;

    // v[i] — current assignment
    private volatile int value = -1;

    // domain[i] — original, never modified
    private final List<Integer> domain;

    // current_domain[i] — shrinks as values are tried and removed
    private final List<Integer> currentDomain;

    // conf_set[i] — past agents that caused conflict FOR THIS agent
    private final Set<Integer> confSet;

    // future_fc[i] — future agents whose domain THIS agent pruned
    private final Set<Integer> futureFc;

    // reductions[j] — stack: each entry = set of values removed from j by one check_forward call
    private final Map<Integer, Deque<Set<Integer>>> reductions;

    // This agent's local view of future agents' current domains
    private final Map<Integer, Set<Integer>> futureCurrentDomain;

    // receivedValues[j] = last known assignment of agent j (from ASSIGNMENT messages)
    private final int[] receivedValues;

    // past_fc[i] stack for this agent
    private final Deque<Integer> pastFcStack;

    // past_fc[j] map for future agents
    private final Map<Integer, Deque<Integer>> pastFc;

    private volatile boolean running = true;

    // ── Constructor ──────────────────────────────────────────
    public Agent(int id, int n, Mailer mailer) {
        this.id = id;
        this.n = n;
        this.mailer = mailer;

        // Initialize neighbors collection (all future agents whom we prune/communicate with)
        this.neighbors = new ArrayList<>();
        for (int j = id + 1; j < n; j++) {
            neighbors.add(j);
        }

        domain = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            domain.add(v);
        }
        currentDomain = Collections.synchronizedList(new ArrayList<>(domain));

        confSet = Collections.synchronizedSet(new LinkedHashSet<>());
        futureFc = Collections.synchronizedSet(new LinkedHashSet<>());
        reductions = new ConcurrentHashMap<>();
        futureCurrentDomain = new ConcurrentHashMap<>();
        receivedValues = new int[n];
        Arrays.fill(receivedValues, -1);

        pastFcStack = new ArrayDeque<>();
        pastFc = new ConcurrentHashMap<>();

        for (int j = id + 1; j < n; j++) {
            reductions.put(j, new ArrayDeque<>());
            pastFc.put(j, new ArrayDeque<>());
            Set<Integer> fd = new LinkedHashSet<>();
            for (int v = 0; v < n; v++) {
                fd.add(v);
            }
            futureCurrentDomain.put(j, fd);
        }
    }

    // ── run() ────────────────────────────────────────────────
    @Override
    public void run() {
        while (running) {
            Message msg = mailer.readOne(id);
            if (msg == null) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
                continue;
            }
            try {
                handleMessage(msg);
            } finally {
                mailer.removeMessage(id, msg);
            }
        }
    }

    // ── handleMessage ────────────────────────────────────────
    private void handleMessage(Message msg) {
        switch (msg.getType()) {

            case ASSIGNMENT -> {
                if (msg.getSender() < id) {
                    receivedValues[msg.getSender()] = msg.getValue();
                }
            }

            case ACTIVATE -> {
                collectAll();
                update_current_domain(id);
                fc_cbj_label_and_proceed();
            }

            case BACKTRACK -> {
                int target = msg.getValue();
                if (id == target) {
                    Set<Integer> newConf = new LinkedHashSet<>();
                    synchronized (confSet) {
                        newConf.addAll(confSet);
                    }
                    newConf.addAll(msg.getBacktrackConflictSet());
                    newConf.remove(id);
                    synchronized (confSet) {
                        confSet.clear();
                        confSet.addAll(newConf);
                    }

                    undo_reductions(id);

                    // Reset future agents' state in local maps to prevent stale domain check
                    for (int j = id + 1; j < n; j++) {
                        Deque<Set<Integer>> redStack = reductions.get(j);
                        if (redStack != null) {
                            synchronized (redStack) {
                                redStack.clear();
                            }
                        }
                        Deque<Integer> pfStack = pastFc.get(j);
                        if (pfStack != null) {
                            synchronized (pfStack) {
                                pfStack.clear();
                            }
                        }
                        futureCurrentDomain.get(j).clear();
                        for (int v = 0; v < n; v++) {
                            futureCurrentDomain.get(j).add(v);
                        }
                    }

                    if (value != -1) {
                        currentDomain.remove(Integer.valueOf(value));
                    }
                    value = -1;

                    for (int j = id + 1; j < n; j++) {
                        receivedValues[j] = -1;
                    }

                    collectAll();
                    fc_cbj_label_and_proceed();
                } else if (id > target) {
                    resetState();
                }
            }

            case SOLUTION -> running = false;
            case STOP -> running = false;
        }
    }

    // ── collectAll ───────────────────────────────────────────
    private void collectAll() {
        for (int j = 0; j < id; j++) {
            while (receivedValues[j] == -1 && running) {
                Message msg = mailer.readOne(id);
                if (msg == null) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                } else if (msg.getType() == Message.Type.ASSIGNMENT) {
                    mailer.removeMessage(id, msg);
                    if (msg.getSender() < id) {
                        receivedValues[msg.getSender()] = msg.getValue();
                    }
                } else {
                    mailer.removeMessage(id, msg);
                    handleMessage(msg);
                }
            }
        }
    }

    private void fc_cbj_label_and_proceed() {
        int nextAgent = fc_cbj_label();

        if (nextAgent == id + 1) {
            System.out.println("  Agent " + id + " → row " + value);
            sendAssignmentToFuture();
            if (id == n - 1) {
                broadcastSolution();
            } else {
                sendActivate(id + 1);
            }
        } else {
            value = -1;
            int h = fc_cbj_unlabel();
            if (h < 0) {
                System.out.println("  No solution exists.");
                broadcastStop();
                running = false;
            } else {
                System.out.println("  Agent " + id + ": backjump → Agent " + h);
                sendBacktrack(h);
            }
        }
    }

    // ── FC_CBJ_LABEL ─────────────────────────────────────────
    private int fc_cbj_label() {
        boolean consistent = false;
        int failedJ = -1;

        List<Integer> domainCopy;
        synchronized (currentDomain) {
            domainCopy = new ArrayList<>(currentDomain);
        }

        System.out.println("  Agent " + id + " label: domainCopy = " + domainCopy + ", value = " + value);

        for (int v : domainCopy) {
            value = v;
            consistent = true;

            for (int j = id + 1; j < n; j++) {
                consistent = check_forward(id, j);
                if (!consistent) {
                    failedJ = j;
                    break;
                }
            }

            if (!consistent) {
                currentDomain.remove(Integer.valueOf(value));
                undo_reductions(id);
                if (failedJ >= 0) {
                    Agent failedAgent = mailer.getAgent(failedJ);
                    if (failedAgent != null) {
                        synchronized (confSet) {
                            Deque<Integer> jPastFc = failedAgent.getPastFcStack();
                            if (!jPastFc.isEmpty()) {
                                confSet.add(jPastFc.peek());
                            }
                        }
                    }
                }
            } else {
                break;
            }
        }

        if (consistent) {
            return id + 1;
        } else {
            return id;
        }
    }

    // ── check_forward ─────────────────────────────────────────
    private boolean check_forward(int i, int j) {
        Agent agentJ = mailer.getAgent(j);
        Set<Integer> jDomain = futureCurrentDomain.get(j);
        Set<Integer> reduction = new LinkedHashSet<>();
        for (int vj : jDomain) {
            if (!isConsistent(i, value, j, vj)) {
                reduction.add(vj);
            }
        }

        if (!reduction.isEmpty()) {
            jDomain.removeAll(reduction);
            Deque<Set<Integer>> redStack = reductions.get(j);
            synchronized (redStack) {
                redStack.push(reduction);
            }
            Deque<Integer> pfStack = pastFc.get(j);
            synchronized (pfStack) {
                pfStack.push(i);
            }
            futureFc.add(j);

            if (agentJ != null) {
                agentJ.pushPastFcStack(i);
            }
        }

        return !jDomain.isEmpty();
    }

    // ── FC_CBJ_UNLABEL ────────────────────────────────────────
    private int fc_cbj_unlabel() {
        int maxConf;
        int maxPastFc;
        synchronized (confSet) {
            maxConf = maxList(confSet);
        }
        synchronized (pastFcStack) {
            maxPastFc = maxList(pastFcStack);
        }
        int h = Math.max(maxConf, maxPastFc);

        if (h >= 0) {
            Agent agentH = mailer.getAgent(h);
            if (agentH != null) {
                Set<Integer> myConf = new LinkedHashSet<>();
                synchronized (confSet) {
                    myConf.addAll(confSet);
                }
                agentH.setConfSet(union(agentH.getConfSet(), myConf));
            }

            undo_reductions(id);
            update_current_domain(id);

            if (value != -1) {
                currentDomain.remove(Integer.valueOf(value));
            }
        }

        return h;
    }

    /** Helper: union two sets into a new LinkedHashSet */
    private Set<Integer> union(Set<Integer> a, Set<Integer> b) {
        Set<Integer> result = new LinkedHashSet<>(a);
        result.addAll(b);
        return result;
    }

    // ── undo_reductions ───────────────────────────────────────
    public void undo_reductions(int i) {
        if (i != id) return;
        for (int j : new ArrayList<>(futureFc)) {
            Deque<Set<Integer>> stack = reductions.get(j);
            Set<Integer> reduction = null;
            if (stack != null) {
                synchronized (stack) {
                    if (!stack.isEmpty()) {
                        reduction = stack.pop();
                    }
                }
            }

            if (reduction != null) {
                Set<Integer> jLocalDomain = futureCurrentDomain.get(j);
                if (jLocalDomain != null) {
                    jLocalDomain.addAll(reduction);
                }

                Agent agentJ = mailer.getAgent(j);
                if (agentJ != null) {
                    agentJ.popPastFcStack();
                }
                Deque<Integer> pfStack = pastFc.get(j);
                if (pfStack != null) {
                    synchronized (pfStack) {
                        if (!pfStack.isEmpty()) {
                            pfStack.pop();
                        }
                    }
                }
            }
        }
        futureFc.clear();
    }

    // ── update_current_domain ─────────────────────────────────
    public void update_current_domain(int i) {
        if (i != id) return;
        currentDomain.clear();
        currentDomain.addAll(domain);
        
        // Reapply active reductions that past agents imposed on this agent's domain
        for (int k = 0; k < id; k++) {
            Agent pastAgent = mailer.getAgent(k);
            if (pastAgent != null) {
                Deque<Set<Integer>> activeReductions = pastAgent.getReductionsFor(id);
                if (activeReductions != null) {
                    for (Set<Integer> reduction : activeReductions) {
                        currentDomain.removeAll(reduction);
                    }
                }
            }
        }
    }

    // ── resetState ────────────────────────────────────────────
    private void resetState() {
        value = -1;
        synchronized (confSet) {
            confSet.clear();
        }
        undo_reductions(id);
        update_current_domain(id);
        Arrays.fill(receivedValues, -1);
        for (int j = id + 1; j < n; j++) {
            Deque<Set<Integer>> redStack = reductions.get(j);
            if (redStack != null) {
                synchronized (redStack) {
                    redStack.clear();
                }
            }
            Deque<Integer> pfStack = pastFc.get(j);
            if (pfStack != null) {
                synchronized (pfStack) {
                    pfStack.clear();
                }
            }
            futureCurrentDomain.get(j).clear();
            for (int v = 0; v < n; v++) {
                futureCurrentDomain.get(j).add(v);
            }
        }
    }

    // ── N-Queens constraint ───────────────────────────────────
    private boolean isConsistent(int col1, int row1, int col2, int row2) {
        if (row1 == row2) return false;
        if (Math.abs(row1 - row2) == Math.abs(col1 - col2)) return false;
        return true;
    }

    // ── Messaging ─────────────────────────────────────────────
    private void sendAssignmentToFuture() {
        List<Message> pastMsgs = new ArrayList<>();
        for (int k = 0; k < id; k++) {
            if (receivedValues[k] != -1) {
                pastMsgs.add(new Message(Message.Type.ASSIGNMENT, k, receivedValues[k]));
            }
        }
        // Send assignments only to explicit neighbors (all future agents)
        for (int j : neighbors) {
            mailer.send(j, new Message(Message.Type.ASSIGNMENT, id, value));
            for (Message pastMsg : pastMsgs) {
                mailer.send(j, pastMsg);
            }
        }
    }

    private void sendActivate(int target) {
        mailer.send(target, new Message(Message.Type.ACTIVATE, id, target));
    }

    private void sendBacktrack(int h) {
        Set<Integer> backtrackConflicts = new LinkedHashSet<>();
        synchronized (confSet) {
            backtrackConflicts.addAll(confSet);
        }
        for (int j = h; j < n; j++) {
            mailer.send(j, new Message(Message.Type.BACKTRACK, id, h,
                    new LinkedHashSet<>(backtrackConflicts)));
        }
    }

    private void broadcastSolution() {
        int[] sol = Arrays.copyOf(receivedValues, n);
        sol[id] = value;
        System.out.println("-----------------------------");
        System.out.print("Solution Found!  ");
        StringBuilder sb = new StringBuilder();
        for (int v : sol) {
            sb.append(v).append(" ");
        }
        System.out.println(sb.toString().trim());
        for (int j = 0; j < n; j++) {
            if (j != id) {
                mailer.send(j, new Message(Message.Type.SOLUTION, id, Arrays.copyOf(sol, n)));
            }
        }
        running = false;
    }

    private void broadcastStop() {
        Message msg = new Message(Message.Type.STOP, id, -1);
        for (int j = 0; j < n; j++) {
            if (j != id) {
                mailer.send(j, msg);
            }
        }
        running = false;
    }

    private int maxList(Collection<Integer> col) {
        int max = -1;
        for (int v : col) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    public int getId() {
        return id;
    }

    public int getValue() {
        return value;
    }

    /**
     * Consistency flag indicating whether the current assignment is valid (Requirement 33).
     * Derived dynamically from the current assignment state to avoid redundant class fields.
     */
    public boolean isAssignmentConsistent() {
        return value != -1;
    }

    public void setValue(int val) {
        this.value = val;
    }

    public Set<Integer> getConfSet() {
        synchronized (confSet) {
            return new LinkedHashSet<>(confSet);
        }
    }

    public void setConfSet(Set<Integer> set) {
        synchronized (confSet) {
            this.confSet.clear();
            this.confSet.addAll(set);
        }
    }

    public void clearConfSet() {
        synchronized (confSet) {
            this.confSet.clear();
        }
    }

    public Deque<Integer> getPastFcStack() {
        synchronized (pastFcStack) {
            return new ArrayDeque<>(pastFcStack);
        }
    }

    public void pushPastFcStack(int val) {
        synchronized (pastFcStack) {
            pastFcStack.push(val);
        }
    }

    public void popPastFcStack() {
        synchronized (pastFcStack) {
            if (!pastFcStack.isEmpty()) {
                pastFcStack.pop();
            }
        }
    }

    public Deque<Set<Integer>> getReductionsFor(int j) {
        Deque<Set<Integer>> original = reductions.get(j);
        if (original == null) return new ArrayDeque<>();
        synchronized (original) {
            return new ArrayDeque<>(original);
        }
    }

    public List<Integer> getCurrentDomain() {
        return currentDomain;
    }

    public void removeFromCurrentDomain(int val) {
        currentDomain.remove(Integer.valueOf(val));
    }

    public void clearReceivedValues() {
        Arrays.fill(receivedValues, -1);
    }
}
