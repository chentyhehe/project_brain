package com.brain;

import com.brain.server.BrainServer;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        new BrainServer().start();
    }
}
