# Room's generated database implementation is referenced by Room's generated
# code; no package-wide keep rule is needed. Entity/DAO names are not part of
# the persisted schema contract, which keeps R8 free to optimize them.
# Room resolves this concrete generated implementation by class name.
-keep class com.febricahyaa.fluxlab.data.FluxLabDatabase_Impl { *; }

# NativeBridge is reached by System.loadLibrary and JNI's generated symbol
# names, so retain only this concrete bridge and its two native methods.
-keep class com.febricahyaa.fluxlab.benchmark.NativeBridge {
    public static final com.febricahyaa.fluxlab.benchmark.NativeBridge INSTANCE;
    public final native double[] run(int, long, long, long, int);
    public final native void cancel();
}

# Compose, Flux, and SynthesisCore use direct references and require no
# reflection-wide keep rules. Report models are serialized through direct
# adapters, and Room schema access remains generated-code driven.
