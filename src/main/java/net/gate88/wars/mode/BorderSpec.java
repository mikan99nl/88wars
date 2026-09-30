package net.gate88.wars.mode;

/** 特殊ボーダー(パーティクル表現)の仕様。半径は「中心からの半辺の長さ」。 */
public record BorderSpec(boolean enabled, double startRadius, double endRadius,
                         int shrinkStartSeconds, int shrinkEndSeconds, double damagePerSecond) {

    public static final BorderSpec NONE = new BorderSpec(false, 0, 0, 0, 0, 0);

    public double radiusAt(int elapsed) {
        if (elapsed <= shrinkStartSeconds) return startRadius;
        if (elapsed >= shrinkEndSeconds) return endRadius;
        double t = (elapsed - shrinkStartSeconds) / (double) (shrinkEndSeconds - shrinkStartSeconds);
        return startRadius + (endRadius - startRadius) * t;
    }
}
