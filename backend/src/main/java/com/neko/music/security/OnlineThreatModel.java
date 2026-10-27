package com.neko.music.security;

/**
 * 进程内在线逻辑回归模型，用于校准威胁规则的灰区分数。
 *
 * <p>模型采用保守的正/负样本选择：只有高置信攻击特征可以作为正样本，
 * 只有成功且没有拒绝或高危特征的请求可以作为负样本。这样未知流量不会直接
 * 污染模型，服务重启后也会从先验重新开始。</p>
 */
final class OnlineThreatModel {

    private static final int FEATURE_COUNT = 12;
    private static final int MIN_SAMPLES = 32;
    private static final double LEARNING_RATE = 0.035;
    private static final double L2 = 0.0005;
    private static final double INITIAL_BIAS = -3.8;
    private static final double MAX_WEIGHT = 8.0;

    private final double[] weights = new double[FEATURE_COUNT];
    private double bias = INITIAL_BIAS;
    private long samples;

    OnlineThreatModel() {
        // 先验只表达「攻击是少数」，具体特征权重由本地流量学习。
    }

    synchronized Prediction predict(double[] features) {
        if (features == null || features.length != FEATURE_COUNT) {
            return new Prediction(0.0, samples, false);
        }
        double probability = sigmoid(bias + dot(weights, features));
        return new Prediction(probability, samples, samples >= MIN_SAMPLES);
    }

    synchronized void learn(double[] features, boolean attack) {
        if (features == null || features.length != FEATURE_COUNT) {
            return;
        }
        double probability = sigmoid(bias + dot(weights, features));
        double error = (attack ? 1.0 : 0.0) - probability;
        bias = clamp(bias + LEARNING_RATE * error, -MAX_WEIGHT, MAX_WEIGHT);
        for (int i = 0; i < weights.length; i++) {
            double updated = weights[i] * (1.0 - L2) + LEARNING_RATE * error * features[i];
            weights[i] = clamp(updated, -MAX_WEIGHT, MAX_WEIGHT);
        }
        samples = Math.min(Long.MAX_VALUE, samples + 1);
    }

    synchronized long samples() {
        return samples;
    }

    static int featureCount() {
        return FEATURE_COUNT;
    }

    private static double dot(double[] left, double[] right) {
        double result = 0.0;
        for (int i = 0; i < left.length; i++) {
            result += left[i] * right[i];
        }
        return result;
    }

    private static double sigmoid(double value) {
        if (value >= 0) {
            double z = Math.exp(-value);
            return 1.0 / (1.0 + z);
        }
        double z = Math.exp(value);
        return z / (1.0 + z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record Prediction(double probability, long samples, boolean ready) {
    }
}
