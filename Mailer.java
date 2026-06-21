import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Mailer - the ONLY communication channel between agents.
 *
 * Maps each agent ID to its own mailbox (List of Messages).
 * Agents call send() to deliver a message and readOne() to receive.
 *
 * Thread-safe: all access is synchronized.
 */
public class Mailer {

    // Maps agent ID -> its mailbox
    private final HashMap<Integer, List<Message>> map;
    private Agent[] agents;

    public Mailer(int n) {
        map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(i, new LinkedList<>());
        }
    }

    public void registerAgents(Agent[] agents) {
        this.agents = agents;
    }

    public Agent getAgent(int id) {
        if (agents == null || id < 0 || id >= agents.length) {
            return null;
        }
        return agents[id];
    }

    /**
     * Send message m to agent with ID receiver.
     */
    public synchronized void send(int receiver, Message m) {
        map.get(receiver).add(m);
        this.notifyAll(); // Wake up any waiting readOne threads
    }

    /**
     * Read (and remove) one message from agent receiver's mailbox.
     * Non-destructive for control/correctness messages (BACKTRACK, SOLUTION, STOP, ACTIVATE)
     * unless explicitly removed via removeMessage().
     */
    public synchronized Message readOne(int receiver) {
        List<Message> mailbox = map.get(receiver);
        if (mailbox == null || mailbox.isEmpty()) {
            return null;
        }
        Message msg = mailbox.get(0);
        if (msg.getType() == Message.Type.ASSIGNMENT) {
            return mailbox.remove(0); // Destructive for ASSIGNMENT
        }
        return msg; // Non-destructive for control messages
    }

    /**
     * Destructively removes a specific message from the mailbox.
     */
    public synchronized void removeMessage(int receiver, Message msg) {
        List<Message> mailbox = map.get(receiver);
        if (mailbox != null) {
            mailbox.remove(msg);
        }
    }
}
