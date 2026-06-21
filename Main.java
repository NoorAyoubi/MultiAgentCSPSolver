/**
 * Main - launches the Distributed N-Queens FC-CBJ solver.
 *
 * Responsibilities (per assignment):
 *   1. Create the Mailer
 *   2. Create n Agents, each with its own private information only
 *   3. Start each Agent as a Thread
 *   4. Activate Agent 0 to kick off the search
 *   5. Wait for all threads to finish (join)
 *   6. Print final confirmation
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {

        final int n = 8; // 8-Queens problem

        // 1. Create Mailer
        Mailer mailer = new Mailer(n);

        // 2. Create Agents - each gets only its own id, n, and the Mailer
        Agent[]  agents  = new Agent[n];
        Thread[] threads = new Thread[n];

        for (int i = 0; i < n; i++) {
            agents[i]  = new Agent(i, n, mailer);
            threads[i] = new Thread(agents[i], "Agent-" + i);
        }

        mailer.registerAgents(agents);

        // 3. Start all threads
        for (int i = 0; i < n; i++) {
            threads[i].start();
        }

        // 4. Activate Agent 0 - it goes first (per synchronous ordering)
        mailer.send(0, new Message(Message.Type.ACTIVATE, -1, 0));

        // 5. Wait for all threads to finish
        for (int i = 0; i < n; i++) {
            threads[i].join();
        }

        // 6. Done
        System.out.println("Search complete.");
    }
}
