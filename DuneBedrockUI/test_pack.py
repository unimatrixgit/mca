"""Package/engine-contract checks; this cannot replace an actual Bedrock client test."""
from pathlib import Path
import hashlib,json,struct,unittest,uuid,zipfile
ROOT=Path(__file__).resolve().parent
PACK=ROOT/'pack'
ART=ROOT.parent/'DuneResourcePack/assets/dunerealms/textures/gui'
class PackageTests(unittest.TestCase):
 def setUp(self):
  self.hook=json.loads((PACK/'ui/server_form.json').read_text());self.ui=json.loads((PACK/'ui/dunerealms_gui.json').read_text())
 def test_all_archive_json_valid_and_paths_safe(self):
  with zipfile.ZipFile(ROOT/'dist/DuneRealms-Imperial-Navigator-Bedrock-v2.mcpack') as archive:
   self.assertEqual(len(archive.namelist()),len(set(archive.namelist())))
   for name in archive.namelist():
    self.assertFalse(name.startswith('/'));self.assertNotIn('..',Path(name).parts)
    self.assertEqual(archive.read(name),(PACK/name).read_bytes())
    if name.endswith('.json'):json.loads(archive.read(name))
 def test_manifest_identity_distinct_from_existing_items_pack(self):
  manifest=json.loads((PACK/'manifest.json').read_text());ids=[manifest['header']['uuid'],manifest['modules'][0]['uuid']]
  self.assertEqual(len(set(ids)),2)
  for identity in ids:uuid.UUID(identity)
  self.assertNotIn('b82eaa9d-9d58-43aa-b9b9-c903291ea7de',ids)
  self.assertNotIn('635bf2e1-6c24-45a0-8b81-d7b98b743868',ids)
 def test_unrelated_forms_and_custom_forms_not_replaced(self):
  self.assertEqual(set(self.hook),{'namespace','main_screen_content','long_form'})
  change=self.hook['main_screen_content']['modifications'][0]
  self.assertEqual((change['array_name'],change['operation']),('controls','insert_back'))
  factory=change['value'][0]['dunerealms_form_factory']['factory']
  self.assertEqual(factory['control_ids'],{'long_form':'@dunerealms_gui.routed_long_form'})
  native=self.hook['long_form']['modifications'][0]['value'][1]
  custom=self.ui['routed_long_form']['bindings'][1]
  self.assertEqual(native['source_property_name'],"((#title_text - 'DRUI|') = #title_text)")
  self.assertEqual(custom['source_property_name'],"(not ((#title_text - 'DRUI|') = #title_text))")
  for title in ('Staff Operations','A different plugin','Clan Shop','DRUI|Staff Operations','DRUI|Confirm Transfer'):
   routed='DRUI|' in title;native_visible=title.replace('DRUI|','')==title
   self.assertNotEqual(routed,native_visible)
 def test_native_index_and_input_contract_kept(self):
  cards=self.ui['body']['controls'][2]['cards']
  self.assertEqual(cards['collection_name'],'form_buttons')
  self.assertEqual(cards['factory']['name'],'buttons')
  self.assertEqual(cards['bindings'][0],{'binding_name':'#form_button_contents','binding_name_override':'#collection_length'})
  button=self.ui['card']['controls'][0]['action']
  self.assertEqual(button['bindings'],[{'binding_type':'collection_details','binding_collection_name':'form_buttons'}])
  self.assertEqual(button['button_mappings'],[{'from_button_id':'button.menu_select','to_button_id':'button.form_button_click','mapping_type':'pressed'}])
  # No collection index remapping, no added pseudo-buttons, no command or privilege data in client pack.
  text=json.dumps(self.ui)
  for forbidden in ('collection_index','run_command','uuid','permission','console'):
   self.assertNotIn(forbidden,text)
 def test_all_owned_ui_and_texture_references_exist(self):
  def walk(value):
   if isinstance(value,dict):
    for key,item in value.items():
     if '@dunerealms_gui.' in key:self.assertIn(key.split('@dunerealms_gui.')[1],self.ui)
     if key=='texture' and isinstance(item,str) and item.startswith('textures/dunerealms/'):
      self.assertTrue((PACK/(item+'.png')).is_file(),item)
     if isinstance(item,str) and item.startswith(('@dunerealms_gui.','dunerealms_gui.')):self.assertIn(item.split('.')[-1],self.ui)
     walk(item)
   elif isinstance(value,list):
    for item in value:walk(item)
  walk(self.ui);walk(self.hook)
  defs=json.loads((PACK/'ui/_ui_defs.json').read_text());self.assertTrue(all((PACK/path).is_file() for path in defs['ui_defs']))
 def test_existing_imperial_art_is_unchanged(self):
  for source in ART.glob('*.png'):
   target=PACK/'textures/dunerealms/ui'/source.name
   if target.exists():self.assertEqual(hashlib.sha256(source.read_bytes()).digest(),hashlib.sha256(target.read_bytes()).digest())
 def test_touch_rows_focus_states_and_scrolling(self):
  button=self.ui['card']['controls'][0]['action']
  self.assertGreaterEqual(button['size'][1],44)
  controls={next(iter(c)):next(iter(c.values())) for c in button['controls']}
  for state in ('default_control','hover_control','pressed_control','locked_control'):self.assertIn(button[state],controls)
  self.assertEqual(self.ui['routed_long_form']['size'],['100% - 16px','100% - 16px'])
  self.assertEqual(self.ui['routed_long_form']['max_size'],[420,340])
  self.assertEqual(self.ui['header']['anchor_from'],'top_middle')
  self.assertEqual(self.ui['header']['anchor_to'],'top_middle')
  scroll=self.ui['routed_long_form']['controls'][2]['scroll@common.scrolling_panel']
  self.assertEqual(scroll['$scrolling_content'],'dunerealms_gui.body')
 def test_code_drawn_pngs_have_valid_dimensions_and_signature(self):
  for name in ('panel','card','card-hover','card-pressed'):
   data=(PACK/f'textures/dunerealms/ui/{name}.png').read_bytes();self.assertEqual(data[:8],b'\x89PNG\r\n\x1a\n');self.assertEqual(struct.unpack('>II',data[16:24]),(16,16))
if __name__=='__main__':unittest.main()

