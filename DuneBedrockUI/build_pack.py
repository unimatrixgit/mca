from pathlib import Path
import json,struct,zlib,binascii,hashlib,zipfile,shutil
ROOT=Path(__file__).resolve().parent
PACK=ROOT/'pack'
OUT=ROOT/'dist'
MARKER='DRUI|'
ART=ROOT.parent/'DuneResourcePack/assets/dunerealms/textures/gui'
def write_json(name,data):
 path=PACK/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
def png(name,fill,border):
 # A code-drawn 9-slice UI primitive, not an edit of any artwork.
 rows=[]
 for y in range(16):
  row=bytearray()
  for x in range(16):
   edge=min(x,y,15-x,15-y)
   colour=border if edge==0 else fill
   if (x<2 and y<2) or (x>13 and y<2) or (x<2 and y>13) or (x>13 and y>13):colour=(0,0,0,0)
   row.extend(colour)
  rows.append(b'\0'+row)
 def chunk(tag,data):return struct.pack('>I',len(data))+tag+data+struct.pack('>I',binascii.crc32(tag+data)&0xffffffff)
 target=PACK/'textures/dunerealms/ui'/name;target.parent.mkdir(parents=True,exist_ok=True)
 target.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b''))
def binding(name,target=None,kind=None):
 result={'binding_name':name}
 if target:result['binding_name_override']=target
 if kind:result.update(binding_type=kind,binding_collection_name='form_buttons')
 return result
def view(source,target):return {'binding_type':'view','source_property_name':source,'target_property_name':target}
def state(texture):
 return {'type':'image','texture':'textures/dunerealms/ui/'+texture,'size':['100%','100%'],'nineslice_size':3,'layer':0}
def build():
 PACK.mkdir(exist_ok=True);OUT.mkdir(exist_ok=True)
 write_json('manifest.json',{'format_version':2,'header':{'name':'DuneRealms — Imperial Navigator','description':'Imperial framed menus. Native form callbacks; text fallback supported.','uuid':'04cf44d8-edf8-4cd5-ac58-fad90da8635b','version':[1,1,0],'min_engine_version':[1,21,100]},'modules':[{'type':'resources','uuid':'315ee668-8026-4e58-8fb1-c81b430f17cb','version':[1,1,0]}]})
 write_json('ui/_ui_defs.json',{'ui_defs':['ui/dunerealms_gui.json']})
 active="(not ((#title_text - 'DRUI|') = #title_text))"
 inactive="((#title_text - 'DRUI|') = #title_text)"
 write_json('ui/server_form.json',{'namespace':'server_form','main_screen_content':{'modifications':[{'array_name':'controls','operation':'insert_back','value':[{'dunerealms_form_factory':{'type':'panel','size':['100%','100%'],'factory':{'name':'server_form_factory','control_ids':{'long_form':'@dunerealms_gui.routed_long_form'}}}}]}]},'long_form':{'modifications':[{'array_name':'bindings','operation':'insert_back','value':[binding('#title_text'),view(inactive,'#visible')]}]}})
 title={'type':'label','size':['100% - 82px',32],'offset':[72,36],'anchor_from':'top_left','anchor_to':'top_left','text':'#text','font_scale_factor':1.2,'color':[.92,.80,.51],'shadow':False,'bindings':[binding('#title_text'),view("(#title_text - 'DRUI|')",'#text')]}
 header={'type':'panel','size':['100%',92],'anchor_from':'top_middle','anchor_to':'top_middle','controls':[{'crest':{'type':'image','texture':'textures/dunerealms/ui/imperial-portal','size':[40,40],'offset':[24,36],'anchor_from':'top_left','anchor_to':'top_left'}},{'title':title},{'subtitle':{'type':'label','text':'DUNEREALMS  /  IMPERIAL NAVIGATOR','color':[.59,.72,.69],'font_scale_factor':.65,'size':['100% - 82px',12],'offset':[72,64],'anchor_from':'top_left','anchor_to':'top_left'}}]}
 card_label={'type':'label','text':'#form_button_text','size':['100% - 56px','default'],'anchor_from':'left_middle','anchor_to':'left_middle','offset':[48,0],'color':[.94,.88,.71],'shadow':False,'layer':4,'bindings':[binding('#form_button_text',kind='collection')]}
 card_image={'type':'image','size':[34,34],'offset':[8,0],'anchor_from':'left_middle','anchor_to':'left_middle','layer':3,'bindings':[binding('#form_button_texture','#texture','collection'),binding('#form_button_texture_file_system','#texture_file_system','collection'),view("(not ((#texture = '') or (#texture = 'loading')))",'#visible')]}
 card={'type':'panel','size':['100%',54],'controls':[{'action':{'type':'button','size':['100%',48],'anchor_from':'top_middle','anchor_to':'top_middle','sound_name':'random.click','default_control':'normal','hover_control':'hover','pressed_control':'pressed','locked_control':'normal','button_mappings':[{'from_button_id':'button.menu_select','to_button_id':'button.form_button_click','mapping_type':'pressed'}],'bindings':[{'binding_type':'collection_details','binding_collection_name':'form_buttons'}],'controls':[{'normal':state('card')},{'hover':state('card-hover')},{'pressed':state('card-pressed')},{'icon':card_image},{'label':card_label}]}}]}
 body={'type':'stack_panel','size':['100% - 12px','100%c'],'orientation':'vertical','anchor_from':'top_middle','anchor_to':'top_middle','controls':[{'description':{'type':'label','text':'#form_text','size':['100%','default'],'color':[.73,.82,.77],'bindings':[binding('#form_text')]}},{'spacer':{'type':'panel','size':['100%',8]}},{'cards':{'type':'stack_panel','size':['100%','100%c'],'orientation':'vertical','factory':{'name':'buttons','control_ids':{'button':'dunerealms_gui.card','label':'@server_form.dynamic_label','header':'@server_form.dynamic_header','divider':'@settings_common.option_group_section_divider'}},'collection_name':'form_buttons','bindings':[binding('#form_button_contents','#collection_length')]}}]}
 # Width/height caps remain inside the viewport at mobile GUI scales. Native scroll handles arbitrary record text.
 screen={'type':'panel','size':['100% - 16px','100% - 16px'],'max_size':[420,340],'bindings':[binding('#title_text'),view(active,'#visible')],'controls':[{'background':{'type':'image','texture':'textures/dunerealms/ui/panel','nineslice_size':3,'size':['100%','100%'],'alpha':.98,'layer':0}},{'header@dunerealms_gui.header':{'layer':2}},{'scroll@common.scrolling_panel':{'size':['100% - 56px','100% - 112px'],'offset':[0,96],'anchor_from':'top_middle','anchor_to':'top_middle','$show_background':False,'$scrolling_content':'dunerealms_gui.body','$scroll_size':[5,'100% - 4px'],'$scrolling_pane_size':['100% - 6px','100% - 2px'],'$scrolling_pane_offset':[0,0],'layer':2}}]}
 screen['controls'].append({'ornament':{'type':'image','texture':'textures/dunerealms/ui/imperial-frame-v1','size':['100%','100%'],'layer':1}})
 write_json('ui/dunerealms_gui.json',{'namespace':'dunerealms_gui','routed_long_form':screen,'header':header,'body':body,'card':card})
 png('panel.png',(9,18,33,253),(181,146,63,255));png('card.png',(15,33,42,255),(181,146,63,255));png('card-hover.png',(37,79,63,255),(237,205,118,255));png('card-pressed.png',(12,43,34,255),(237,205,118,255))
 for source in ['imperial-frame-v1.png','imperial-portal.png','faction-atreides-aerdrecht-heraldic.png','faction-corrino-korenholt-heraldic.png','faction-fremen-vrijduin-heraldic.png','faction-harkonnen-harkveen-heraldic.png','faction-ordos-oordwijk-heraldic.png']:
  shutil.copyfile(ART/source,PACK/'textures/dunerealms/ui'/source)
 target=OUT/'DuneRealms-Imperial-Navigator-Bedrock-v2.mcpack'
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
  for path in sorted(PACK.rglob('*')):
   if path.is_file():
    info=zipfile.ZipInfo(path.relative_to(PACK).as_posix(),(2026,10,30,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,path.read_bytes())
 print(target)
 print('SHA256 '+hashlib.sha256(target.read_bytes()).hexdigest())
if __name__=='__main__':build()


