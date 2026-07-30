"""
CyvoraX Suite - Extension Plugin Loader
Plugin system allowing custom Python extensions to hook into request/response pipelines.
"""
import importlib.util
import os


class ExtensionHook:
    def on_request(self, msg):
        pass

    def on_response(self, req_msg, resp_msg):
        pass


class ExtensionManager:
    def __init__(self):
        self.extensions = []

    def load_extension(self, filepath: str) -> bool:
        if not os.path.exists(filepath):
            return False
        try:
            spec = importlib.util.spec_from_file_location("cyvorax_ext", filepath)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            
            if hasattr(module, "Extension") and issubclass(module.Extension, ExtensionHook):
                instance = module.Extension()
                self.extensions.append(instance)
                return True
        except Exception as e:
            print(f"[Extender Error] Could not load {filepath}: {e}")
        return False

    def trigger_on_request(self, msg):
        for ext in self.extensions:
            try:
                ext.on_request(msg)
            except Exception as e:
                print(f"[Extender Error] on_request: {e}")

    def trigger_on_response(self, req_msg, resp_msg):
        for ext in self.extensions:
            try:
                ext.on_response(req_msg, resp_msg)
            except Exception as e:
                print(f"[Extender Error] on_response: {e}")
