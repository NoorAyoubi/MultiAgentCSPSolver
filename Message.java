import java.util.Arrays;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Immutable message passed between agents via the Mailer.
 */
public final class Message {

    public enum Type {
        ASSIGNMENT,
        BACKTRACK,
        ACTIVATE,
        SOLUTION,
        STOP
    }

    private final Type   type;
    private final int    sender;
    private final int    value;
    private final int[]  solution;
    private final Set<Integer> backtrackConflictSet;

    public Message(Type type, int sender, int value) {
        this.type     = type;
        this.sender   = sender;
        this.value    = value;
        this.solution = null;
        this.backtrackConflictSet = new LinkedHashSet<>();
    }

    public Message(Type type, int sender, int value, Set<Integer> backtrackConflictSet) {
        this.type     = type;
        this.sender   = sender;
        this.value    = value;
        this.solution = null;
        this.backtrackConflictSet = new LinkedHashSet<>(backtrackConflictSet);
    }

    public Message(Type type, int sender, int[] solution) {
        this.type     = type;
        this.sender   = sender;
        this.value    = -1;
        this.solution = Arrays.copyOf(solution, solution.length);
        this.backtrackConflictSet = new LinkedHashSet<>();
    }

    public Type  getType()        { return type; }
    public int   getSender()      { return sender; }
    public int   getValue()       { return value; }
    public int   getTargetAgent() { return value; }

    public int[] getSolution() {
        return solution == null ? null : Arrays.copyOf(solution, solution.length);
    }

    public Set<Integer> getBacktrackConflictSet() {
        return new LinkedHashSet<>(backtrackConflictSet);
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", sender=" + sender + ", value=" + value + "}";
    }
}
