// This file is inert in a browser. Only the bundled Android app supplies RemotyIR.
if (typeof RemotyIR !== 'undefined') {
  let scanIndex = -1;
  const profiles = [{id:'samsung',name:'Samsung'},{id:'lg',name:'LG'}];
  const originalRender = render, originalCard = card, originalDeviceModal = deviceModal;
  card = function(d) {
    let html=originalCard(d);
    if(d.irProfile) html=html.replace(/<span class="status[^>]*">[^<]*<\/span>/,'<span class="status">ريموت IR محفوظ</span>');
    return html;
  };
  render = function() {
    originalRender();
    document.querySelectorAll('.notice').forEach(n=>{
      if(n.textContent.includes('نسخة تجريبية')||n.textContent.includes('الأجهزة والريموت')) n.textContent='التحكم الحقيقي متاح فقط لملفات تلفزيون Samsung وLG التي تم العثور عليها بإشارات IR. بقية الأجهزة والوضع الليلي تجريبية.';
    });
    document.querySelectorAll('.demo-pill').forEach(n=>n.innerHTML='<span></span> يدعم IR');
  };
  discover = function() {
    scanIndex=-1;
    if(!RemotyIR.available()) {
      modal('مرسل IR غير متاح', '<p>لم يعثر التطبيق على مرسل أشعة تحت الحمراء مدعوم في هذا الهاتف. تأكد من موديل الجهاز ودعمه لواجهة Android Consumer IR.</p>'); return;
    }
    scanScreen();
  };
  function scanScreen(message='') {
    const current=scanIndex>=0?profiles[scanIndex]:null;
    modal('البحث بإشارات IR', `<div class="radar">${icon('signal')}</div><p style="text-align:center"><strong style="color:#edf6eb">وجّه أعلى الهاتف نحو التلفزيون</strong><br>سنجرب إشارات تشغيل شائعة لتلفزيونات Samsung وLG. قد تنطفئ الشاشة عند الاستجابة؛ اضغط «لقيته» بعد استجابتها. هذا ليس تعرّفاً تلقائياً.</p><p class="remote-note" style="text-align:center">${message|| (current?'آخر إشارة: '+current.name:'اضغط بدء التجربة لإرسال أول إشارة')}</p><div class="modal-actions"><button class="primary-btn" data-ir="next">${scanIndex<0?'بدء التجربة':'جرّب الإشارة التالية'}</button>${current?'<button class="secondary-btn" data-ir="found">لقيته ✓</button>':''}</div><p style="text-align:center">${scanIndex+1} / ${profiles.length} ملفات إشارات</p>`);
  }
  function saveScreen(){let p=profiles[scanIndex];if(!p)return;modal('حفظ الريموت',`<p>تم اختيار ملف ${p.name}. سمِّ الجهاز واختر غرفته.</p><form id="ir-form" class="form-grid"><label>اسم الجهاز<input name="name" required maxlength="50" value="تلفزيون ${p.name}"></label><label>الغرفة<select name="room">${state.rooms.map(r=>`<option>${esc(r)}</option>`).join('')}</select></label><button class="primary-btn">حفظ الريموت</button></form>`)}
  deviceModal = function(id) {
    let d=state.devices.find(x=>x.id===id);
    if(!d || !d.irProfile) return originalDeviceModal(id);
    modal('ريموت التلفزيون', `<div class="remote-panel"><span class="device-icon blue">${icon('tv')}</span><h3>${esc(d.name)}</h3><p>${esc(d.room)} · ${esc(d.irProfile.toUpperCase())}</p><p>وجّه الهاتف إلى التلفزيون ثم اضغط الزر.</p><div class="remote-controls"><button data-ir-send="power" data-id="${esc(id)}" aria-label="تشغيل أو إطفاء">${icon('power')}</button><button data-ir-send="up" data-id="${esc(id)}" aria-label="رفع الصوت">صوت +</button><button data-ir-send="down" data-id="${esc(id)}" aria-label="خفض الصوت">صوت −</button></div><div class="modal-actions"><button class="secondary-btn" data-ir-send="prev" data-id="${esc(id)}">القناة −</button><button class="secondary-btn" data-ir-send="next" data-id="${esc(id)}">القناة +</button><button class="secondary-btn" data-ir-send="mute" data-id="${esc(id)}">كتم الصوت</button></div><p class="remote-note">لا يستطيع التطبيق معرفة الحالة الفعلية للجهاز أو تأكيد وصول الإشارة.</p><div class="modal-actions"><button class="secondary-btn" data-delete="${esc(id)}">حذف الجهاز</button></div></div>`);
  };
  document.addEventListener('click',function(e){
    let b=e.target.closest('button');if(!b)return;
    if(b.dataset.ir){e.stopImmediatePropagation();feedback();if(b.dataset.ir==='next'){
      if(scanIndex+1>=profiles.length){scanScreen('انتهت الإشارات المتاحة. إذا لم يستجب التلفزيون فهذا الموديل غير مدعوم حالياً.');return}
      const next=scanIndex+1;const error=RemotyIR.send(profiles[next].id,'power');if(error){toast(error);return}scanIndex=next;scanScreen();
    }else if(b.dataset.ir==='found')saveScreen();return}
    if(b.dataset.irSend){e.stopImmediatePropagation();feedback();let d=state.devices.find(x=>x.id===b.dataset.id);if(!d||!d.irProfile)return;let error=RemotyIR.send(d.irProfile,b.dataset.irSend);toast(error||'تم إرسال الإشارة؛ تحقق من استجابة التلفزيون');return}
    if(['export','import','install'].includes(b.dataset.action)){e.stopImmediatePropagation();toast('هذه الميزة غير متاحة داخل نسخة أندرويد الحالية');}
  },true);
  document.addEventListener('submit',function(e){if(e.target.id!=='ir-form')return;e.preventDefault();e.stopImmediatePropagation();let f=new FormData(e.target),name=String(f.get('name')).trim(),room=f.get('room'),p=profiles[scanIndex];if(!name||!state.rooms.includes(room)||!p)return;state.devices.push({id:Date.now().toString(36)+Math.random().toString(36).slice(2),name,type:'tv',room,irProfile:p.id,favorite:true,on:false});save();closeModal();page='devices';filter=null;render();toast('تم حفظ الريموت');},true);
  render();
}
