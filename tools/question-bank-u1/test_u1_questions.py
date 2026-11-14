import os
import sys
import unittest
from unittest.mock import patch

import u1_questions


class U1QuestionRunnerTest(unittest.TestCase):
    def test_apply_requires_a_confirmed_window_before_loading_credentials(self):
        with patch.object(sys, 'argv', ['u1_questions.py', 'apply', '--session', '30', '--account', 'sample']), \
                patch('u1_questions.evaluation_database_config') as config:
            with self.assertRaises(SystemExit) as failure:
                u1_questions.main()
        self.assertEqual(failure.exception.code, 2)
        config.assert_not_called()

    def test_inspection_passes_only_selected_database_values_to_children(self):
        selected = {'EVALUATION_DB_URL': 'fixed-evaluation-url',
                    'EVALUATION_DB_USERNAME': 'sample-user', 'EVALUATION_DB_PASSWORD': 'sample-password'}
        args = ['u1_questions.py', 'inspect', '--session', '30', '--account', 'sample']
        with patch.object(sys, 'argv', args), \
                patch.dict(os.environ, {'DB_PASSWORD': 'unrelated-password'}), \
                patch('u1_questions.evaluation_database_config', return_value=selected), \
                patch('u1_questions.os.chdir'), \
                patch('u1_questions.Path.read_text', return_value='sample-classpath'), \
                patch('u1_questions.subprocess.run') as compile_call, \
                patch('u1_questions.subprocess.call', return_value=0) as java_call:
            self.assertEqual(u1_questions.main(), 0)
        for call in (compile_call, java_call):
            env = call.call_args.kwargs['env']
            self.assertEqual({key: env[key] for key in selected}, selected)
            self.assertNotIn('DB_PASSWORD', env)
            self.assertNotIn('sample-password', ' '.join(call.call_args.args[0]))
        self.assertEqual(java_call.call_args.kwargs['env']['U1_EVALUATION_ACCOUNT'], 'sample')


if __name__ == '__main__':
    unittest.main()
