package io.github.gdacoff.killthefrizz.sleeping;

/**
 * Enumerates every reason {@link IdleChecker#shouldSleep} may reject a mob from
 * sleeping (skipping AI). Used for diagnostic counters surfaced via
 * {@code /killthefrizz stats}.
 */
public enum RejectionReason {
    DISABLED("Mod disabled"),
    AUTO_DISABLED("Auto-disabled (slowdown)"),
    SLEEPING_DISABLED("SleepingEntities disabled in config"),
    PLAYER_NEARBY("Player within sleep radius"),
    HEARTBEAT("Periodic heartbeat AI tick"),
    FORCE_AWAKE("Recent damage / wakeup timer"),
    HAS_TARGET("Has attack target"),
    NAVIGATING("Active navigation path"),
    NOT_ON_GROUND("Not on ground / fall-flying"),
    IN_FLUID("In water or lava"),
    VELOCITY("Moving (velocity > threshold)"),
    PASSENGER_OR_LEASHED("Passenger, vehicle or leashed"),
    TAMED("Tamed animal"),
    IN_LOVE("Breeding / in love"),
    BABY("Baby animal"),
    BURNING_OR_HURT_OR_EFFECTS("Burning, hurt, or active effects"),
    BLACKLISTED("Entity type in blacklist");

    private final String displayName;

    RejectionReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
