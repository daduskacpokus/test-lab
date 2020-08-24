package com.myorg;

import software.amazon.awscdk.core.App;

import java.util.Arrays;

public class PayeverApp {
    public static void main(final String[] args) {
        App app = new App();

        new PayeverStack(app, "PayeverStack");

        app.synth();
    }
}
