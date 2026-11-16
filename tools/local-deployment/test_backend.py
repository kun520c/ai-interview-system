#!/usr/bin/env python3
"""Offline safety checks for the local-only launcher; no credentials or services."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("local_backend", Path(__file__).with_name("backend.py"))
backend = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backend)


class LocalBackendSafetyTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name) / "providers.env"
        self.values = {
            "DEEPSEEK_BASE_URL": "https://api.deepseek.com",
            "DEEPSEEK_API_KEY": "test-only",
            "EMBEDDING_BASE_URL": "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "EMBEDDING_API_KEY": "test-only",
        }

    def tearDown(self):
        self.directory.cleanup()

    def write_values(self):
        self.path.write_text("".join(key + "=" + value + "\n" for key, value in self.values.items()))
        self.path.chmod(0o600)

    def test_local_provider_file_uses_fixed_embedding_defaults(self):
        self.write_values()
        result = backend.provider_config(self.path)
        self.assertEqual(result["EMBEDDING_DIMENSION"], "1024")
        self.assertEqual(result["EMBEDDING_PROFILE_VERSION"], "qwen3.7-text-embedding-1024-dense-v1")

    def test_insecure_provider_file_is_rejected(self):
        self.write_values()
        self.path.chmod(0o644)
        with self.assertRaises(ValueError):
            backend.provider_config(self.path)

    def test_unexpected_provider_host_is_rejected(self):
        self.values["DEEPSEEK_BASE_URL"] = "https://other.example"
        self.write_values()
        with self.assertRaises(ValueError):
            backend.provider_config(self.path)

    def test_incompatible_embedding_profile_is_rejected(self):
        self.values["EMBEDDING_PROFILE_VERSION"] = "different-profile"
        self.write_values()
        with self.assertRaises(ValueError):
            backend.provider_config(self.path)

    def test_evaluation_named_provider_file_is_rejected(self):
        with self.assertRaises(ValueError):
            backend.provider_config(Path(self.directory.name) / "evaluation-provider.env")

    def test_other_database_refused_before_network_or_provider_access(self):
        settings = {"DB_URL": "jdbc:mysql://127.0.0.1:3308/ai_interview_evaluation"}
        with patch.object(backend, "protected_config", return_value=settings), \
             patch.object(backend, "database_preflight") as database, \
             patch.object(backend, "provider_config") as provider, \
             patch("sys.argv", ["backend.py", "--check"]):
            with self.assertRaises(ValueError):
                backend.main()
            database.assert_not_called()
            provider.assert_not_called()

    def test_relay_listener_identity_mismatch_is_rejected_before_connecting(self):
        state = Path(self.directory.name) / "runtime.json"
        token = Path(self.directory.name) / "relay-token"
        state.write_text('{"relay_pid": 123}')
        token.write_text("test-only")
        state.chmod(0o600)
        token.chmod(0o600)
        responses = [SimpleNamespace(stdout="999\n"), SimpleNamespace(stdout="python /expected/relay.py\n")]
        with patch.object(backend, "RELAY_STATE", state), patch.object(backend, "RELAY_TOKEN", token), \
             patch.object(backend, "RELAY_SCRIPT", Path("/expected/relay.py")), \
             patch.object(backend.subprocess, "run", side_effect=responses), \
             patch.object(backend.socket, "create_connection") as connect:
            with self.assertRaises(ValueError):
                backend.provider_config(None)
            connect.assert_not_called()

    def test_active_task_requires_explicit_acknowledgement(self):
        settings = {"DB_URL": "jdbc:mysql://127.0.0.1:3308/ai_interview", "DB_USERNAME": "local-user",
                    "DB_PASSWORD": "test-only", "JWT_SECRET": "test-only"}
        with patch.object(backend, "protected_config", return_value=settings), \
             patch.object(backend, "sha256", return_value=backend.JAR_SHA), \
             patch.object(backend, "provider_config", return_value={}), \
             patch.object(backend, "database_preflight", return_value=1), \
             contextlib.redirect_stdout(io.StringIO()):
            with patch("sys.argv", ["backend.py", "--check"]), self.assertRaises(ValueError):
                backend.main()
            with patch("sys.argv", ["backend.py", "--check", "--allow-pending"]):
                backend.main()


if __name__ == "__main__":
    unittest.main()
