export const AUTOSAVE_INTERVAL=10*60*1000;
// Background timers may be delayed. A focus/visibility check catches up once, never duplicates a running save.
export function createAutosaveClock(save:()=>Promise<void>,now:()=>number=Date.now) {
  let last=now(),running=false;
  return async function tick() {
    if(running||now()-last<AUTOSAVE_INTERVAL)return false;
    running=true;last=now();
    try{await save();return true;}finally{running=false;}
  };
}
