import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from config import EVALUATION_DB_BASE_URL, EVALUATION_DB_URL, evaluation_database_config, protected_config
from run import evaluation_environment


class EvaluationDatabaseConfigTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.directory.chmod(0o700)
        self.path = self.directory / 'evaluation-db.env'

    def write_config(self, contents):
        self.path.write_text(contents, encoding='utf-8')
        self.path.chmod(0o600)

    def test_loads_only_explicit_database_fields_and_fixes_the_target(self):
        self.write_config("EVALUATION_DB_USERNAME=sample\nEVALUATION_DB_PASSWORD='sample password'\n"
                          f'EVALUATION_DB_URL={EVALUATION_DB_BASE_URL}\n')
        self.assertEqual(evaluation_database_config(self.path), {
            'EVALUATION_DB_USERNAME': 'sample',
            'EVALUATION_DB_PASSWORD': 'sample password',
            'EVALUATION_DB_URL': EVALUATION_DB_URL,
        })

    def test_rejects_other_schema_and_unexpected_fields(self):
        base = 'EVALUATION_DB_USERNAME=sample\nEVALUATION_DB_PASSWORD=sample\n'
        for extra in ('EVALUATION_DB_URL=jdbc:mysql://127.0.0.1:3308/ai_interview\n',
                      'UNRELATED_SECRET=sample\n', 'EVALUATION_DB_USERNAME=duplicate\n'):
            with self.subTest(extra=extra):
                self.write_config(base + extra)
                with self.assertRaises(ValueError):
                    evaluation_database_config(self.path)

    def test_rejects_missing_credentials_and_shell_expressions(self):
        for contents in ('EVALUATION_DB_USERNAME=sample\n',
                         'EVALUATION_DB_USERNAME=sample\nEVALUATION_DB_PASSWORD=$(command)\n'):
            with self.subTest(contents=contents):
                self.write_config(contents)
                with self.assertRaises(ValueError):
                    evaluation_database_config(self.path)

    def test_rejects_root_even_when_present_in_the_dedicated_file(self):
        self.write_config('EVALUATION_DB_USERNAME=root\nEVALUATION_DB_PASSWORD=sample\n')
        with self.assertRaises(ValueError):
            evaluation_database_config(self.path)

    def test_requires_owner_only_directory_and_regular_file(self):
        self.write_config('EVALUATION_DB_USERNAME=sample\nEVALUATION_DB_PASSWORD=sample\n')
        self.path.chmod(0o644)
        with self.assertRaises(ValueError):
            evaluation_database_config(self.path)
        self.path.chmod(0o600)
        self.directory.chmod(0o755)
        with self.assertRaises(ValueError):
            evaluation_database_config(self.path)
        self.directory.chmod(0o700)
        original = self.directory / 'actual.env'
        self.path.rename(original)
        self.path.symlink_to(original)
        with self.assertRaises(ValueError):
            evaluation_database_config(self.path)

    def test_generic_protected_config_remains_usable(self):
        self.write_config('EXAMPLE=sample\n')
        self.assertEqual(protected_config(self.path), {'EXAMPLE': 'sample'})

    def test_runner_does_not_read_project_env_or_inherit_other_db_fields(self):
        selected = {'EVALUATION_DB_USERNAME': 'sample', 'EVALUATION_DB_PASSWORD': 'sample',
                    'EVALUATION_DB_URL': EVALUATION_DB_URL}
        with patch.dict(os.environ, {'DB_PASSWORD': 'unrelated', 'EVALUATION_DB_PASSWORD': 'other'}), \
                patch('run.evaluation_database_config', return_value=selected), \
                patch('run.protected_config', side_effect=AssertionError('unexpected config read')):
            env = evaluation_environment(paid=False)
        self.assertEqual({key: env[key] for key in selected}, selected)
        self.assertNotIn('DB_PASSWORD', env)


if __name__ == '__main__':
    unittest.main()
