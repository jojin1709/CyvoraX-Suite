"""
CyvoraX Suite - Java Burp Extension Bridge
Embedded JVM initialization and Burp Suite extension loader using JPype.
"""
import os
import sys

_JPYPE_AVAILABLE = False
try:
    import jpype
    import jpype.imports
    _JPYPE_AVAILABLE = True
except ImportError:
    _JPYPE_AVAILABLE = False


class JavaBurpBridge:
    def __init__(self):
        self.jvm_started = False
        self.loaded_jars = []

    def start_jvm(self, classpath: list = None) -> bool:
        if not _JPYPE_AVAILABLE:
            print("[JavaBridge] JPype1 is not installed. Run 'pip install JPype1' to enable Java Burp extension support.")
            return False

        if jpype.isJVMStarted():
            self.jvm_started = True
            return True

        try:
            cp_arg = "-Djava.class.path=" + os.path.pathsep.join(classpath or [])
            jpype.startJVM(jpype.getDefaultJVMPath(), cp_arg, "-Xcheck:jni")
            self.jvm_started = True
            print("[JavaBridge] Embedded JVM started successfully.")
            return True
        except Exception as e:
            print(f"[JavaBridge Error] Failed to start JVM: {e}")
            return False

    def load_extension_jar(self, jar_path: str) -> bool:
        if not self.jvm_started:
            if not self.start_jvm([jar_path]):
                return False
        
        if os.path.exists(jar_path):
            jpype.addClassPath(jar_path)
            self.loaded_jars.append(jar_path)
            print(f"[JavaBridge] Extension JAR loaded: {jar_path}")
            return True
        return False
