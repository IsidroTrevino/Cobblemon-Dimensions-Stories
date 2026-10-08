package com.cobblemondimensions.stories.minigame;

/** Marks completion before applying rewards, so closing or reentry cannot pay twice. */
final class SessionSettlement {
    private boolean settled;

    boolean runOnce(Runnable payout) {
        if (settled) {
            return false;
        }
        settled = true;
        payout.run();
        return true;
    }

    void discard() {
        settled = true;
    }
}
