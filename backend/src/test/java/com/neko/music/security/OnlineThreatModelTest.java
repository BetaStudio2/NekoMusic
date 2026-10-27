package com.neko.music.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineThreatModelTest {

    @Test
    void learnsSeparationFromConservativeLabels() {
        OnlineThreatModel model = new OnlineThreatModel();
        double[] normal = new double[OnlineThreatModel.featureCount()];
        double[] attack = normal.clone();
        attack[0] = 1.0;
        attack[1] = 1.0;
        attack[5] = 1.0;
        attack[8] = 1.0;

        for (int i = 0; i < 40; i++) {
            model.learn(normal, false);
        }
        for (int i = 0; i < 12; i++) {
            model.learn(attack, true);
        }

        OnlineThreatModel.Prediction normalPrediction = model.predict(normal);
        OnlineThreatModel.Prediction attackPrediction = model.predict(attack);
        assertTrue(normalPrediction.ready());
        assertTrue(attackPrediction.probability() > normalPrediction.probability());
    }
}
