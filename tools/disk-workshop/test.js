globalThis.runDiskTests = function () {
  const D=DosDisk,check=(condition,message)=>{if(!condition)throw new Error(message);};
  const rejects=fn=>{let threw=false;try{fn();}catch{threw=true;}check(threw,'Expected rejection');};
  const bytes=new Uint8Array(D.SIZE);bytes.set([74,86,67,80,77,4,253,1]);bytes[1000]=77;
  const disk=new D.Disk(bytes);disk.put('HELLO.TXT',D.textBytes('Hello!\r\nA\tB'),1);
  check(D.textOf(disk.list()[0].data)==='Hello!\nA    B','Text normalization');
  check(disk.bytes[1000]===77&&bytes[D.BASE]===0,'Original/boot preservation');
  rejects(()=>disk.put('BIG.TXT',new Uint8Array(D.MAX+1),1));rejects(()=>D.textBytes('é'));
  const rgba=new Uint8Array([255,0,0,255,0,255,0,255,0,0,255,255,255,255,255,255]);
  const image=D.image(2,2,rgba);check(D.decodeImage(image).pixels.join()==='96,28,3,127','Palette conversion');disk.put('PIC.IMG',image,2);
  const largeImage=D.image(240,240,new Uint8Array(240*240*4).fill(255));
  check(largeImage.length===57604&&D.decodeImage(largeImage).pixels.every(c=>c===127),'Maximum image round trip');
  rejects(()=>D.image(241,1,new Uint8Array(241*4)));
  rejects(()=>D.decodeImage(largeImage.slice(0,-1)));
  const badPixels=largeImage.slice();badPixels[300]=128;rejects(()=>D.decodeImage(badPixels));

  const tune=D.notes('60 3\n64 3\n67 6');check(D.decodeTune(tune).length===6,'Tune composer');disk.put('TUNE.JMT',tune,3);
  const file=track=>Uint8Array.from([77,84,104,100,0,0,0,6,0,0,0,1,1,224,77,84,114,107,0,0,track.length>>8,track.length&255,...track]);
  const midi=file([0,144,60,100,131,96,128,60,0,0,255,47,0]);
  const events=D.decodeTune(D.midi(midi));check(events.length===2&&events[0].frames===15&&events[1].control===0,'MIDI note-on/off');
  const running=D.decodeTune(D.midi(file([0,144,60,100,131,96,60,0,0,255,47,0])));check(running[1].control===0,'Running status');
  const tempo=D.decodeTune(D.midi(file([0,255,81,3,3,208,144,0,144,60,100,131,96,128,60,0,0,255,47,0])));check(tempo[0].frames===8,'Tempo conversion');
  const long=D.decodeTune(D.midi(file([0,144,60,100,129,160,0,128,60,0,0,255,47,0])));check(long.reduce((n,e)=>n+e.frames,0)===646,'Long duration splitting');
  rejects(()=>D.midi(midi.slice(0,-1)));
  const dense=D.decodeTune(D.midi(file([
    ...Array.from({length:8},(_,i)=>[0,144,60+i,100]).flat(),
    131,96,144,68,100,131,96,128,60,0,131,96,128,68,0,
    0,144,69,100,131,96,144,70,100,0,255,47,0
  ])));
  check(dense.slice(0,8).every((e,i)=>e.voice===i),'First eight notes use distinct voices');
  check(dense[8].note===68&&dense[8].voice===0&&dense[8].control===129,'Ninth note replaces oldest voice');
  check(dense[8].frames===30&&dense[9].note===68&&dense[9].control===0,'Stolen note release preserves replacement and timing');
  check(dense[10].note===69&&dense[10].voice===0,'Released voice is reused');
  check(dense[11].note===70&&dense[11].voice===1,'Next overflow replaces oldest remaining note');
  check(dense.every(e=>e.voice>=0&&e.voice<8)&&dense.slice(-8).every(e=>e.control===0),'All remaining voices stop');
  const voice7=D.tune([{frames:1,voice:7,note:60,control:129}]);
  check(D.decodeTune(voice7)[0].voice===7,'Eighth voice round trip');
  rejects(()=>D.tune([{frames:1,voice:16,note:60,control:129}]));
  const extended=D.tune(Array.from({length:15299},()=>({frames:0,voice:0,note:60,control:129})));
  check(extended.length===61200&&extended[2]===2&&D.decodeTune(extended).length===15299,'Maximum extended tune');
  rejects(()=>D.tune(Array.from({length:15300},()=>({frames:0,voice:0,note:60,control:129}))));
  rejects(()=>D.decodeTune(extended.slice(0,-1)));
  const invalidHeader=extended.slice();invalidHeader[3]=1;rejects(()=>D.decodeTune(invalidHeader));
  disk.remove(0);check(disk.list().length===2,'Delete');for(let i=0;i<253;i++)disk.put(`F${i}.TXT`,D.textBytes('X'),1);
  rejects(()=>disk.put('FULL.TXT',D.textBytes('Y'),1));check(new D.Disk(disk.bytes).list().length===255,'Disk round trip');
  disk.bank=255;disk.put('OTHER.TXT',D.textBytes('Z'),1);check(disk.list().length===1,'Bank isolation');check(disk.clone().bank===255,'Clone bank');disk.bank=2;check(disk.list().length===255,'Original bank preserved');rejects(()=>disk.remove(255));
  const blank=new Uint8Array(D.SIZE);blank.set([74,86,67,80,77,7,253,1]);
  const linked=new D.Disk(blank,3);
  for(const length of [0,239,240,241,255,256,257,4096,61200]){
    const payload=Uint8Array.from({length},(_,i)=>i&255);linked.put('LARGE.BIN',payload);
    const entry=linked.list()[0];check(entry.data.length===length&&entry.data.every((b,i)=>b===payload[i]),'Linked round trip '+length);
    check(entry.blocks.length===Math.max(1,Math.ceil(length/240)),'Block count '+length);
  }
  linked.put('LARGE.BIN',new Uint8Array(240));linked.put('SECOND.BIN',new Uint8Array(30000));
  const before=linked.bytes.slice();rejects(()=>linked.put('LARGE.BIN',new Uint8Array(40000)));
  check(linked.bytes.every((b,i)=>b===before[i]),'Full-bank overwrite is unchanged');
  linked.remove(linked.list().find(f=>f.name==='SECOND.BIN').slot);linked.remove(0);check(!linked.list().length,'Chain deletion');
  linked.put('BAD.BIN',new Uint8Array(500));const malformed=linked.bytes.slice();malformed[(3<<16)+256+15]=1;
  rejects(()=>new D.Disk(malformed,3));malformed[(3<<16)+256+15]=255;rejects(()=>new D.Disk(malformed,3));
  const longTune=D.tune(Array.from({length:255},()=>({frames:0,voice:0,note:60,control:0})));
  check(longTune.length===1024&&D.decodeTune(longTune).length===255,'Expanded tune limit');
  for(let revision=4;revision<=20;revision++) {
    const current=new Uint8Array(D.SIZE);current.set([74,86,67,80,77,revision,253,1]);
    new D.Disk(current);
  }
  const folders=new Uint8Array(D.SIZE);folders.set([74,86,67,80,77,14,253,1]);
  const fd=new D.Disk(folders);fd.put('DOCS',new Uint8Array());fd.bytes[D.BASE+13]=4;
  fd.put('SAME.TXT',D.textBytes('ROOT'),1);fd.cwd=1;
  fd.put('SAME.TXT',D.textBytes('CHILD'),1);
  check(fd.list().length===1&&D.textOf(fd.list()[0].data)==='CHILD','Folder name isolation');
  const fc=fd.clone();check(fc.cwd===1&&fc.list()[0].parent===1,'Clone keeps folder');
  fc.put('SAME.TXT',D.textBytes('UPDATED'),1);fc.cwd=0;
  check(D.textOf(fc.list().find(f=>f.name==='SAME.TXT').data)==='ROOT','Child edit preserves root');
  rejects(()=>fc.remove(0));rejects(()=>fc.put('DOCS',D.textBytes('BAD'),1));
  fc.cwd=1;fc.remove(fc.list()[0].slot);fc.cwd=0;fc.remove(0);
  check(fc.list().length===1,'Remove empty folder');
  const spill=new D.Disk(blank);
  spill.put('PHOTOS',new Uint8Array());spill.bytes[D.BASE+13]=4;spill.cwd=1;
  spill.put('FIRST.BIN',new Uint8Array(40000));
  check(spill.capacity().used===168&&spill.capacity().freeBytes===20880,'Capacity includes all folders');
  const original=spill.bytes.slice(D.BASE,D.BASE+65536);
  const placed=spill.importFile('SECOND.BIN',new Uint8Array(30000).fill(91));
  check(placed.bank===3&&spill.bank===3&&spill.cwd===1,'Overflow selects another bank and folder');
  check(spill.all().find(f=>f.type===4).name==='PHOTOS','Overflow preserves folder path');
  check(spill.list()[0].data.every(b=>b===91),'Overflow content preserved');
  check(original.every((b,i)=>b===spill.bytes[D.BASE+i]),'Overflow preserves original bank');
  spill.importFile('THIRD.TXT',D.textBytes('Next'),1);
  check(spill.bank===3&&spill.list().length===2,'Subsequent imports reuse available bank');
  const stable=spill.bytes.slice();
  rejects(()=>spill.importFile('SECOND.BIN',new Uint8Array(D.MAX),0));
  check(spill.bank===3&&stable.every((b,i)=>b===spill.bytes[i]),'Existing files never silently move');
  spill.put('FILL.BIN',new Uint8Array(30000));
  const invalid=spill.bytes.slice();
  rejects(()=>spill.importFile('INVALID.IMG',new Uint8Array(30000),2));
  check(spill.bank===3&&invalid.every((b,i)=>b===spill.bytes[i]),'Invalid overflow import is atomic');
  const occupied=new D.Disk(blank);
  occupied.put('FULL.BIN',new Uint8Array(D.MAX));
  for(let bank=3;bank<=255;bank++)occupied.bytes[(bank<<16)+1]=65;
  const full=occupied.bytes.slice();
  rejects(()=>occupied.importFile('NEW.TXT',D.textBytes('X'),1));
  check(full.every((b,i)=>b===occupied.bytes[i])&&occupied.bank===2,'No empty bank leaves disk unchanged');
  if(typeof DosDesktop!=='undefined') {
    const payload=Uint8Array.from(atob(DosDesktop),c=>c.charCodeAt(0));
    check(payload.length>240&&payload.length<=D.MAX,'Desktop COM length');
    const appDisk=new D.Disk(folders);appDisk.put('JCTRL.COM',payload,0);
    check(appDisk.list().find(f=>f.name==='JCTRL.COM').data.every((b,i)=>b===payload[i]),'Bundled desktop linked import');
  }
  return 'PASS: linked disk round trip, preservation, size limits, text encoding, palette images, MIDI tempo/running status/polyphony, malformed inputs and long durations.';
};
