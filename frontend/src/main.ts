import { Component, signal, computed } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Keycloak from 'keycloak-js';
import { parseAmount, sumAmounts, formatMoney, positiveAmount } from './money.mjs';

interface Account { id:string; name:string; currency:string; balance:string; version:number }
interface Movement { id:string; amount:string; balance:string; description:string; occurredAt:string; sequence:number }
interface Audit { event_id:string; account_id:string; amount:string; balance:string; sequence:number; occurred_at:string }
const identity = new Keycloak({url:'http://localhost:8180',realm:'portfolio',clientId:'web'});

@Component({
 selector:'app-root', standalone:true, imports:[CommonModule, FormsModule],
 templateUrl:'./app.html'
})
class App {
 readonly ready=signal(false); readonly signedIn=signal(false);
 readonly busy=signal(false); readonly error=signal(''); readonly notice=signal('');
 readonly accounts=signal<Account[]>([]); readonly selected=signal<Account|null>(null);
 readonly history=signal<Movement[]>([]); readonly audit=signal<Audit[]>([]);
 readonly section=signal<'accounts'|'audit'>('accounts');
 readonly totalUsd=computed(()=>sumAmounts(this.accounts().filter(a=>a.currency==='USD').map(a=>a.balance)));
 readonly money=formatMoney; readonly positive=positiveAmount;
 name=''; currency='USD'; amount=''; description='';
 private pendingKey:string|null=null;
 private pendingPayload='';
 constructor(){void this.init();}
 async init(){
  try{
   this.signedIn.set(await identity.init({onLoad:'check-sso',pkceMethod:'S256',checkLoginIframe:false}));
   if(this.signedIn()) await this.load();
  }catch{this.error.set('Identity service is unavailable. Wait a moment and reload this page.');}
  finally{this.ready.set(true);}
 }
 login(){void identity.login({redirectUri:window.location.origin});}
 logout(){void identity.logout({redirectUri:window.location.origin});}
 private async api<T>(path:string,method='GET',body?:unknown,key?:string):Promise<T>{
  await identity.updateToken(30);
  const response=await fetch(path,{method,headers:{Authorization:'Bearer '+identity.token,'Content-Type':'application/json',...(key?{'Idempotency-Key':key}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});
  if(!response.ok){
   const problem=await response.json().catch(()=>({}));
   throw new Error(problem.detail || (response.status===429?'Too many requests. Please wait and retry.':'Request failed ('+response.status+'). Please retry.'));
  }
  return response.json();
 }
 async load(){
  this.accounts.set(await this.api<Account[]>('/api/accounts'));
  const old=this.selected();
  if(old){this.selected.set(this.accounts().find(a=>a.id===old.id)||null);this.history.set(await this.api<Movement[]>('/api/accounts/'+old.id+'/movements'));}
 }
 async act(work:()=>Promise<void>){
  if(this.busy())return;
  this.busy.set(true);this.error.set('');this.notice.set('');
  try{await work();}catch(e){this.error.set(e instanceof Error?e.message:'An unexpected error occurred.');}
  finally{this.busy.set(false);}
 }
 async create(){
  await this.act(async()=>{
   const name=this.name.trim();if(!name)throw new Error('Enter an account name.');
   const account=await this.api<Account>('/api/accounts','POST',{name,currency:this.currency});
   this.name='';this.selected.set(account);await this.load();this.notice.set('Account created.');
  });
 }
 async choose(account:Account){
  await this.act(async()=>{this.selected.set(account);this.history.set(await this.api<Movement[]>('/api/accounts/'+account.id+'/movements'));});
 }
 async post(){
  await this.act(async()=>{
   const account=this.selected();if(!account)return;
   const amount=parseAmount(this.amount);
   const description=this.description.trim();if(!description)throw new Error('Enter a description.');
   const payload=JSON.stringify({account:account.id,amount,description});
   if(this.pendingPayload!==payload){this.pendingKey=crypto.randomUUID();this.pendingPayload=payload;}
   await this.api('/api/accounts/'+account.id+'/movements','POST',{amount,description},this.pendingKey!);
   this.pendingKey=null;this.pendingPayload='';this.amount='';this.description='';
   await this.load();this.notice.set('Movement recorded. Audit delivery may take a few seconds.');
  });
 }
 showAudit(){void this.act(async()=>{this.audit.set(await this.api<Audit[]>('/api/audit'));this.section.set('audit');});}
 refresh(){void this.act(async()=>{if(this.section()==='audit')this.audit.set(await this.api<Audit[]>('/api/audit'));else await this.load();});}
}
bootstrapApplication(App).catch(()=>{document.body.textContent='Application could not start. Please reload.';});
