package pw.vodes.anitomy.internal;

final class JniBindings {
    private JniBindings() {
    }

    static native byte[] parse(byte[] input, int options);
}
