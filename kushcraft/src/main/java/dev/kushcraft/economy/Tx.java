package dev.kushcraft.economy;

/**
 * Every kind of economy event in the transaction log (ledger table).
 * {@code took} = the player handed items from their inventory for this money
 * (their inventory is saved before the payment is written, see Persistence).
 */
public enum Tx {
    START("starting money", false),
    SELL("sold product", true),
    ORDER("market contract", true),
    SHIPMENT("cartel shipment delivery", true),
    BUY("Shop purchase", false),
    TRADE("Trade purchase", false),
    PAY_OUT("sent money", false),
    PAY_IN("received money", false),
    DEATH("cash lost on death", false),
    RANK_UP("rank-up", false),
    WORKER_HIRE("worker placed", false),
    WORKER_DISMISS("worker dismissed", false),
    WORKER_TRAIN("worker training", false),
    WAGES("worker wages", false),
    WORKER_BUY("worker bought supplies", false),
    RUNNER_SALE("Runner sales", false),
    WORKER_AWAY("worker output while away", false),
    WORKER_RAID("worker knocked out", false),
    LAB_UPGRADE("Drug Lab upgrade", false),
    MIX("strain mix", false),
    CARTEL_CREATE("cartel started", false),
    CARTEL_DEPOSIT("cartel bank deposit", false),
    CARTEL_WITHDRAW("cartel bank withdrawal", false),
    CARTEL_BANK("cartel bank income", false),
    AWARD("award reward", false),
    JOB("jobs pay", false),
    REFUND("refund", false),
    ADMIN("admin change", false),
    VAULT_IN("paid by another plugin", false),
    VAULT_OUT("taken by another plugin", false),
    RESET("season reset", false);

    private final String label;
    private final boolean took;

    Tx(String label, boolean took) {
        this.label = label;
        this.took = took;
    }

    public String label() {
        return label;
    }

    /** True when the player handed items from their inventory for this money. */
    public boolean took() {
        return took;
    }
}
