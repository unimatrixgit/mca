import json
import threading
import unittest
from unittest.mock import patch
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import agent

class AgentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        agent.SECRET = 't' * 64
        cls.http = ThreadingHTTPServer(('127.0.0.1', 0), agent.Handler)
        threading.Thread(target=cls.http.serve_forever, daemon=True).start()
        cls.url = 'http://127.0.0.1:' + str(cls.http.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()

    def request(self, path, body=None, token='t' * 64):
        data = None if body is None else json.dumps(body).encode()
        request = Request(self.url + path, data=data, headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
        try:
            with urlopen(request) as response:
                return response.status, json.load(response)
        except HTTPError as response:
            return response.code, json.load(response)

    def test_authentication_and_fixed_log_services(self):
        self.assertEqual(self.request('/servers', token='wrong')[0], 401)
        self.assertEqual(self.request('/logs?server=ssh')[0], 404)
        self.assertEqual(self.request('/logs?server=arrakis&server=proxy')[0], 404)

    def test_invalid_actions_never_start_worker(self):
        with patch.object(agent, 'perform') as perform:
            for data in [dict(server='ssh', action='restart', actor='owner'), dict(server='arrakis', action='shell', actor='owner'), dict(server='arrakis', action='command', command='list\nstop', actor='owner'), dict(server='proxy', action='command', command='list', actor='owner')]:
                self.assertEqual(self.request('/action', data)[0], 400)
            perform.assert_not_called()

    def test_accepted_action_and_duplicate_block(self):
        agent.JOBS.clear()
        agent.LAST.clear()
        with patch.object(agent, 'perform') as perform:
            data = dict(server='arrakis', action='restart', actor='owner')
            self.assertEqual(self.request('/action', data)[0], 202)
            self.assertEqual(self.request('/action', data)[0], 409)
            perform.assert_called_once_with('arrakis', 'restart', '', 'owner')

    def test_redacts_credentials(self):
        self.assertNotIn('hunter2', agent.scrub('rcon.password=hunter2'))

if __name__ == '__main__':
    unittest.main()
