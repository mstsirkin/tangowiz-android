import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('endpoints',Path(__file__).parents[1]/'tools/build_musical_endpoints.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class EndpointTests(unittest.TestCase):
 def test_terminal_zero(self):self.assertEqual(5,m.terminal_endpoint([.2]*50+[0]*30,10,1,7))
 def test_stereo_antiphase_is_music(self):self.assertIsNone(m.terminal_endpoint([.2,-.2]*80,10,2,7))
 def test_faint_music_is_not_silence(self):self.assertIsNone(m.terminal_endpoint([.0002]*80,10,1,7))
 def test_interior_pause(self):self.assertIsNone(m.terminal_endpoint([.2]*20+[0]*40+[.2]*20,10,1,7))
 def test_short_tail(self):self.assertIsNone(m.terminal_endpoint([.2]*65+[0]*15,10,1,7))
 def test_timebase_mismatch(self):self.assertIsNone(m.terminal_endpoint([.2]*50+[0]*30,10,1,20))
 def test_all_silent_is_not_song(self):self.assertIsNone(m.terminal_endpoint([0]*80,10,1,7))
if __name__=='__main__':unittest.main()
