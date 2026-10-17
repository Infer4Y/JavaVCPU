/* JV-DOS 1.3 disk format and converters. No network or host VM hooks. */
globalThis.DosDisk = (() => {
  const SIZE=1<<24, BASE=2<<16, SLOTS=255, BLOCK=240, MAX=61200;
  const types=['Binary / COM','Text / JVS','Palette image','MIDI notes','Directory'];
  const fail=message=>{throw new Error(message);};
  const integer=(value,min,max,label)=>{if(!Number.isInteger(value)||value<min||value>max)fail(`${label} must be ${min}–${max}.`);return value;};
  function textBytes(text) {
    text=text.replace(/\r\n?/g,'\n').replace(/\t/g,'    ');
    if([...text].some(c=>c.charCodeAt(0)>126 || (c.charCodeAt(0)<32&&c!=='\n'))) fail('DOS text supports ASCII characters and newlines. Tabs become four spaces.');
    return Uint8Array.from([...text],c=>c.charCodeAt(0));
  }
  const textOf=bytes=>Array.from(bytes,b=>String.fromCharCode(b)).join('');
  function nameOf(name,extension) {
    const base=name.replace(/\.[^.]*$/,'').toUpperCase().replace(/[^A-Z0-9_-]/g,'_').slice(0,8)||'FILE';
    return base+'.'+extension.toUpperCase().slice(0,3);
  }
  class Disk {
    constructor(bytes,bank=2) {
      if(bytes.length!==SIZE)fail('Select a 16 MiB JavaVCPU storage.img.');
      if(textOf(bytes.slice(0,5))!=='JVCPM'||![4,5,6,7,8,9,10,11,12,13,14,15,16].includes(bytes[5])||!bytes[6])fail('This image has no supported JVCPM BIOS header.');
      this.cwd=0; this.bank=integer(bank,2,255,'Bank'); this.bytes=new Uint8Array(bytes); this.list();
    }
    clone(){const copy=new Disk(this.bytes,this.bank);copy.cwd=this.cwd;return copy;}
    all(){return this.list(true);}
    list(all=false){
      const files=[],used=new Set(),base=integer(this.bank,2,255,'Bank')<<16;
      for(let slot=0;slot<SLOTS;slot++){
        const p=base+slot*256;if(!this.bytes[p+1]||this.bytes[p+1]===255)continue;
        let name='';for(let n=1;n<=12&&this.bytes[p+n];n++)name+=String.fromCharCode(this.bytes[p+n]);
        const length=this.bytes[p]+this.bytes[p+14]*256;
        if(length>MAX)fail('File '+name+' exceeds the '+MAX+'-byte limit.');
        const data=new Uint8Array(length),blocks=[];let block=slot,position=0;
        while(true){
          if(used.has(block))fail('File '+name+' has a cyclic or shared block chain.');
          used.add(block);blocks.push(block);const address=base+block*256;
          if(block!==slot&&this.bytes[address+1]!==255)fail('File '+name+' links to a directory entry.');
          const count=Math.min(BLOCK,length-position);data.set(this.bytes.slice(address+16,address+16+count),position);position+=count;
          const next=this.bytes[address+15];
          if(position===length){if(next)fail('File '+name+' has an unexpected tail block.');break;}
          if(!next||next>=SLOTS)fail('File '+name+' has a truncated block chain.');block=next;
        }
        files.push({slot,name,type:this.bytes[p+13],parent:this.bytes[base+255*256+slot+1],data,blocks});
      }
      for(let slot=0;slot<SLOTS;slot++)if(this.bytes[base+slot*256+1]===255&&!used.has(slot))fail('Orphaned continuation block '+slot+'.');
      return all?files:files.filter(f=>f.parent===this.cwd);
    }
    put(name,data,type=0){
      name=name.toUpperCase();if(!/^[A-Z0-9_.-]{1,12}$/.test(name))fail('Names use 1–12 letters, digits, dots, underscores or hyphens.');
      if(data.length>MAX)fail(name+': '+data.length+' bytes exceeds the '+MAX+'-byte limit.');
      integer(type,0,3,'File type');if(type===2)decodeImage(data);if(type===3)decodeTune(data);
      const files=this.list(),old=files.find(f=>f.name===name),base=this.bank<<16;
      if(old?.type===4)fail('Cannot overwrite a directory.');
      let slot=old?.slot;if(slot===undefined)slot=Array.from({length:SLOTS},(_,i)=>i).find(i=>!this.bytes[base+i*256+1]);
      if(slot===undefined)fail('All directory blocks are occupied.');
      const needed=Math.max(1,Math.ceil(data.length/BLOCK)),blocks=old?old.blocks.slice(0,needed):[slot];
      // Preflight capacity: a failed import/overwrite cannot change the original file.
      for(let candidate=1;blocks.length<needed&&candidate<SLOTS;candidate++)
        if(candidate!==slot&&!this.bytes[base+candidate*256+1])blocks.push(candidate);
      if(blocks.length<needed)fail('Not enough free blocks in this bank.');
      for(const released of old?.blocks.slice(needed)||[])this.bytes.fill(0,base+released*256,base+(released+1)*256);
      blocks.forEach((block,index)=>{
        const p=base+block*256;this.bytes.fill(0,p,p+256);
        if(index===0){this.bytes[p]=data.length&255;this.bytes[p+14]=data.length>>>8;this.bytes.set(textBytes(name),p+1);this.bytes[p+13]=type;}
        else{this.bytes[p]=Math.min(BLOCK,data.length-index*BLOCK);this.bytes[p+1]=255;}
        this.bytes[p+15]=blocks[index+1]||0;this.bytes.set(data.slice(index*BLOCK,(index+1)*BLOCK),p+16);
      });
      this.bytes[base+255*256+slot+1]=this.cwd;
      this.bytes[BASE+255*256]=0x4a;return slot;
    }
    remove(slot){
      integer(slot,0,254,'Slot');const file=this.list().find(f=>f.slot===slot);if(!file)fail('Select a file entry.');
      if(file.type===4&&this.all().some(f=>f.parent===slot+1))fail('Directory is not empty.');
      const base=this.bank<<16;this.bytes[base+255*256+slot+1]=0;for(const block of file.blocks)this.bytes.fill(0,base+block*256,base+(block+1)*256);
    }
  }
  function image(width,height,rgba){integer(width,1,240,'Width');integer(height,1,240,'Height');
    if(rgba.length!==width*height*4)fail('Invalid RGBA pixel count.');
    const out=new Uint8Array(4+width*height);out.set([74,73,width,height]);
    for(let p=0;p<width*height;p++){const i=p*4,a=rgba[i+3]/255;
      out[p+4]=(Math.round(rgba[i]*a*3/255)<<5)|(Math.round(rgba[i+1]*a*7/255)<<2)|Math.round(rgba[i+2]*a*3/255);}
    return out;
  }
  function decodeImage(data){if(data[0]!==74||data[1]!==73)fail('Images need a JI header.');
    const width=integer(data[2],1,240,'Width'),height=integer(data[3],1,240,'Height');
    if(data.length!==4+width*height||data.slice(4).some(c=>c>127))fail('Invalid palette image pixels.');return {width,height,pixels:data.slice(4)};}
  function tune(events){if(!events.length||events.length>15299)fail('A tune supports 1-15,299 events (61,200 bytes). Use a shorter MIDI clip.');
    const out=new Uint8Array(4+events.length*4);out.set(events.length<=255?[74,77,1,events.length]:[74,77,2,0]);
    events.forEach((e,i)=>{integer(e.frames,0,255,'Duration');integer(e.voice,0,15,'Voice');integer(e.note,0,127,'MIDI note');
      if(![0,128,129,130,131].includes(e.control))fail('Invalid voice control.');out.set([e.frames,e.voice,e.note,e.control],4+i*4);});return out;}
  function decodeTune(data){if(data[0]!==74||data[1]!==77||!((data[2]===1&&data[3]>0&&data.length===4+data[3]*4)||(data[2]===2&&data[3]===0&&data.length>4&&data.length<=MAX&&(data.length-4)%4===0)))fail('Invalid JM note-stream header.');
    const events=[];for(let i=4;i<data.length;i+=4)events.push({frames:data[i],voice:data[i+1],note:data[i+2],control:data[i+3]});tune(events);return events;}
  function notes(text){const events=[];for(const [i,line] of text.trim().split(/\n/).entries()){
    if(!line.trim())continue;const words=line.trim().split(/\s+/);if(words.length!==2)fail(`Tune line ${i+1}: enter MIDI note and frame count.`);
    const note=integer(Number(words[0]),0,127,'Note'),frames=integer(Number(words[1]),1,255,'Frames');
    events.push({frames,voice:0,note,control:129},{frames:0,voice:0,note,control:0});}return tune(events);}
  function midi(data){
    let p=0;const u8=()=>{if(p>=data.length)fail('Truncated MIDI file.');return data[p++];};
    const u16=()=>u8()*256+u8(),u32=()=>u8()*16777216+u8()*65536+u8()*256+u8();
    const tag=()=>String.fromCharCode(u8(),u8(),u8(),u8());
    const vlq=()=>{let n=0;for(let i=0;i<4;i++){const b=u8();n=n*128+(b&127);if(!(b&128))return n;}fail('Invalid MIDI variable-length value.');};
    if(tag()!=='MThd')fail('Select a Standard MIDI File (.mid).');const header=u32();if(header<6)fail('Invalid MIDI header.');
    const format=u16(),tracks=u16(),ppqn=u16();if(format>1||!tracks||tracks>64||!ppqn||(ppqn&32768))fail('Use MIDI format 0 or 1 with a PPQN time division.');
    p+=header-6;const changes=[];let order=0;
    for(let track=0;track<tracks;track++){
      if(tag()!=='MTrk')fail('Missing MIDI track.');const length=u32(),trackEnd=p+length;
      if(trackEnd>data.length)fail('Truncated MIDI track.');let tick=0,running=0;
      while(p<trackEnd){tick+=vlq();let status=u8();if(status<128){if(!running)fail('MIDI running status is missing.');p--;status=running;}
        if(status===255){const kind=u8(),length=vlq();if(p+length>trackEnd)fail('Truncated MIDI meta event.');
          if(kind===81&&length===3){const tempo=data[p]*65536+data[p+1]*256+data[p+2];if(!tempo)fail('Invalid MIDI tempo.');changes.push({tick,tempo,order:order++});}p+=length;if(kind===47)break;
        }else if(status===240||status===247){const length=vlq();p+=length;running=0;
        }else if(status>=128&&status<240){running=status;const command=status>>4,channel=status&15,a=u8(),b=command===12||command===13?0:u8();
          if(a>127||b>127)fail('Invalid MIDI channel data.');if(command===8||command===9)changes.push({tick,channel,note:a,on:command===9&&b>0,order:order++});
        }else fail('Unsupported MIDI event.');if(p>trackEnd)fail('MIDI event exceeds its track.');
      }p=trackEnd;
    }
    changes.sort((a,b)=>a.tick-b.tick||((a.tempo?0:1)-(b.tempo?0:1))||a.order-b.order);
    let lastTick=0,seconds=0,tempo=500000,previousFrame=0;const active=new Map(),events=[];
    const wait=frames=>{if(!events.length&&frames)events.push({frames:0,voice:0,note:60,control:0});
      if(!events.length)return;while(frames>255){const previous=events[events.length-1];previous.frames=255;events.push({...previous,frames:0});frames-=255;}events[events.length-1].frames=frames;};
    for(const change of changes){seconds+=(change.tick-lastTick)*tempo/ppqn/1e6;lastTick=change.tick;
      if(change.tempo){tempo=change.tempo;continue;}const key=change.channel+':'+change.note;
      if(!change.on&&!active.has(key))continue;const frame=Math.round(seconds/.033);wait(Math.max(0,frame-previousFrame));previousFrame=frame;
      if(change.on){let voice=active.get(key);if(voice===undefined){const occupied=new Set(active.values());voice=[0,1,2,3,4,5,6,7].find(v=>!occupied.has(v));if(voice===undefined){
          // Map insertion order tracks note age; a stolen note's later release is ignored.
          const oldest=active.keys().next().value;voice=active.get(oldest);active.delete(oldest);
        }active.set(key,voice);}
        events.push({frames:0,voice,note:change.note,control:change.channel===9?131:129});
      }else{const voice=active.get(key);events.push({frames:0,voice,note:change.note,control:0});active.delete(key);}
    }
    for(const voice of active.values())events.push({frames:0,voice,note:60,control:0});return tune(events);
  }
  return {Disk,SIZE,BASE,MAX,types,textBytes,textOf,nameOf,image,decodeImage,tune,decodeTune,notes,midi};
})();
