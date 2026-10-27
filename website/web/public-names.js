// Public labels are separate from persistent game IDs and feed contracts.
window.realmNames=(()=>{
  const pairs=[['Project Dune','dunerealms.com'],['DuneRealms','dunerealms.com'],['Sigma Draconis IV','Sigmadraken IV'],['Giedi Prime','Giedermark'],['Atreides','Aerdrecht'],['Harkonnen','Harkveen'],['Corrino','Korenholt'],['Ordos','Oordwijk'],['Fremen','Vrijduin'],['Arrakis','Arrakduin'],['Caladan','Kaladam'],['Kaitain','Kaiterhof'],['Shai-Hulud','Dune Maw'],['Spacing Guild','Vaerkring'],['Guild','Vaerkring'],['Navigators','Routekeepers'],['Navigator','Routekeeper'],['CHOAM','Koopkring'],['Solaris','dunemarks'],['melange','spice'],['stillsuits','reclaimer suits'],['stillsuit','reclaimer suit'],['sietches','rockhavens'],['sietch','rockhaven']];
  pairs.push(['Serevan','Arrakduin'],['Pelareth','Kaladam'],['Varkesh','Giedermark'],['Orivane','Kaiterhof'],['Nivareth','Sigmadraken IV']);
  const rules=pairs.map(([from,to])=>[new RegExp('\\b'+from+'\\b'+(from==='DuneRealms'?'(?!\\.com)':''),'gi'),to]);
  const text=value=>rules.reduce((s,[re,to])=>s.replace(re,to),String(value??''));
  const material=value=>text(String(value).replaceAll('_',' ')).replace(/spice spice/gi,'spice');
  return {text,material};
})();
