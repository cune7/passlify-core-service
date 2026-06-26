# Passlify / Event Ticketing Platform - Master Product & Technical Specification

> **Svrha dokumenta:** ovaj MD fajl je namenjen kao "source of truth" za razvoj ticketing platforme inspirisane najboljim praksama regionalnih platformi za prodaju ulaznica, uključujući javno vidljive Entrio funkcionalnosti. Dokument nije kopija tuđeg proizvoda i ne treba kopirati UI, tekstove, dizajn ili zaštićene elemente. Cilj je da definišemo sopstveni proizvod, prilagođen Srbiji i regionu, sa jednostavnijim admin iskustvom, nižom cenom za organizatore i fleksibilnijom konfiguracijom događaja.

---

## 0. Kako AI agent treba da koristi ovaj dokument

Kada AI agent radi na ovom projektu, ovaj dokument treba da tretira kao glavnu specifikaciju za domenski model, funkcionalnosti, tokove, arhitekturu i redosled implementacije.

AI agent treba da radi po sledećim pravilima:

1. Ne implementirati funkcionalnost koja ruši osnovni tok prodaje karata: event -> ticket type -> checkout -> payment/free order -> ticket issuance -> QR scan.
2. Za sve operacije koje utiču na kapacitete, plaćanja, karte, skeniranje i refundaciju koristiti transakcije, idempotency i audit log.
3. Ne praviti UI/UX kao kopiju Entrio platforme. Može se koristiti isti poslovni koncept, ali ne isti tekst, dizajn, raspored, CSS ili identitet.
4. Svaki feature implementirati tako da podržava multi-tenant model: više organizatora, više događaja, više admina po organizatoru.
5. Sve što je vezano za porez, fiskalizaciju, PDV i isplate tretirati kao konfigurabilno po državi i organizatoru.
6. Prvi cilj je MVP za Srbiju: prodaja karata za događaje, online plaćanje, slanje PDF/QR karata, osnovni admin panel i scanner app/web scanner.
7. Ne optimizovati prerano za stadione, masivne seating planove i kompleksne integracije dok MVP ne bude stabilan.

---

## 1. Vizija proizvoda

Passlify treba da bude kompletna platforma za organizatore događaja:

- kreiranje događaja,
- definisanje kategorija karata,
- online prodaja,
- besplatne registracije,
- prikupljanje podataka o kupcima i posetiocima,
- generisanje i slanje digitalnih karata,
- kontrola ulaza putem QR/barcode skeniranja,
- praćenje prodaje i posećenosti,
- obračun i isplata organizatorima,
- dodatni moduli za promociju, promo kodove, guest liste, akreditacije i on-site prodaju.

### 1.1. Početni positioning

Platforma treba da bude pozicionirana kao jednostavnija, fleksibilnija i pristupačnija alternativa postojećim regionalnim ticketing rešenjima.

Glavni fokus:

- Srbija kao prvo tržište,
- kasnije region: BiH, Crna Gora, Hrvatska, Severna Makedonija, Bugarska,
- organizatori malih i srednjih događaja,
- konferencije, meetupi, koncerti, žurke, festivali, kulturni događaji, sportski događaji,
- niža cena i bolja podrška za lokalne organizatore,
- self-service model, ali sa mogućnošću managed support-a.

### 1.2. Glavna vrednost za organizatora

Organizator treba da može da uradi sledeće bez tehničkog znanja:

1. Kreira event.
2. Doda lokaciju, vreme, opis i vizuale.
3. Kreira više kategorija karata.
4. Definiše cenu za svaku kategoriju.
5. Definiše maksimalan broj karata po kategoriji.
6. Definiše ukupni kapacitet eventa.
7. Definiše da li se podaci unose samo za kupca ili za svaku kartu/posetioca posebno.
8. Kreira custom polja za prijavu.
9. Omogući online plaćanje.
10. Pusti event u prodaju.
11. Prati prodaju uživo.
12. Skenira karte na ulazu.
13. Izveze podatke u Excel/CSV.
14. Dobije obračun i isplatu.

### 1.3. Glavna vrednost za kupca

Kupac treba da ima brz i jednostavan tok:

1. Pronađe event.
2. Izabere kategoriju karte.
3. Izabere količinu.
4. Unese podatke.
5. Plati karticom ili drugim dostupnim metodom.
6. Dobije kartu emailom i/ili u korisničkom nalogu.
7. Pokaže QR kod na ulazu.

---

## 2. Poslovni model

### 2.1. Osnovni model prihoda

Platforma može imati više izvora prihoda:

#### 2.1.1. Provizija po prodatoj karti

Najjednostavniji model:

- X% od bruto cene karte,
- plus eventualno fiksna naknada po karti,
- payment provider trošak se ili uključuje u proviziju ili prikazuje odvojeno.

Primer modela:

- 3% platform fee,
- 30 RSD fixed fee po karti,
- payment processing fee prema banci/provideru,
- opcija da fee plaća kupac ili organizator.

#### 2.1.2. Service fee za kupca

Kupac vidi dodatnu naknadu na checkout-u:

- cena karte: 2.000 RSD,
- servisna naknada: 100 RSD,
- ukupno: 2.100 RSD.

Prednosti:

- organizator dobija jasnu neto cenu,
- platforma monetizuje direktno kroz checkout.

Mane:

- kupci mogu negativno reagovati ako se naknada pojavi tek na kraju,
- potrebno je transparentno prikazati fee već u toku kupovine.

#### 2.1.3. Organizer-paid fee

Organizator plaća platformi naknadu iz prihoda.

Kupac vidi samo cenu karte.

Prednosti:

- bolji UX za kupca,
- jednostavniji marketing: "bez skrivenih troškova".

Mane:

- organizator mora u startu da uračuna fee u cenu karte.

#### 2.1.4. Pretplata za organizatore

Za napredne organizatore:

- Basic: besplatno, plaćanje po prodatoj karti,
- Pro: mesečna pretplata + niži fee,
- Enterprise: custom cena, white-label, integracije, dedicated support.

Pro moduli:

- advanced analytics,
- custom branding,
- custom domain,
- API access,
- CRM export,
- advanced promo engine,
- seating plans,
- multi-zone scanning,
- on-site box office.

#### 2.1.5. On-site services

Dodatna naplata:

- najam skenera,
- osoblje za skeniranje,
- on-site prodajno mesto/blagajna,
- štampa akreditacija,
- setup i podrška na događaju.

#### 2.1.6. Promoted events / marketplace visibility

Ako platforma ima javni marketplace događaja:

- istaknuti event na početnoj strani,
- kategorijski placement,
- newsletter placement,
- social media promo paket,
- remarketing kampanje.

#### 2.1.7. White-label i enterprise licence

Za veće organizatore, festivale, arene, konferencije:

- custom domen,
- custom checkout,
- posebni ugovorni uslovi,
- integracija sa njihovim CRM/POS sistemom,
- dedicated SLA,
- data export i API.

---

## 3. Uloge u sistemu

### 3.1. Platform owner / Super admin

Vlasnik platforme i interni operativni tim.

Mogućnosti:

- upravlja svim organizatorima,
- verifikuje organizatore,
- odobrava događaje ako postoji moderation flow,
- vidi sve transakcije,
- vidi sve isplate,
- podešava provizije,
- upravlja payment providerima,
- rešava support slučajeve,
- radi refundacije ako pravila to dozvoljavaju,
- upravlja globalnim podešavanjima.

### 3.2. Organizacija / Organizer account

Pravno lice, preduzetnik, udruženje, kulturna ustanova ili drugi entitet koji organizuje događaj.

Podaci:

- naziv organizacije,
- pravna forma,
- PIB,
- matični broj,
- adresa,
- odgovorno lice,
- kontakt email,
- telefon,
- bankovni račun,
- status verifikacije,
- ugovoreni fee model,
- payout podešavanja.

### 3.3. Organizer admin

Korisnik koji administrira događaje za organizaciju.

Mogućnosti:

- kreira i menja evente,
- kreira karte,
- prati prodaju,
- izvozi podatke,
- upravlja promo kodovima,
- dodaje članove tima,
- upravlja scanner operatorima.

### 3.4. Event manager

Ograničena uloga za konkretan event.

Mogućnosti:

- menja konkretan event,
- vidi prodaju tog eventa,
- upravlja attendee listom,
- ne vidi finansije cele organizacije.

### 3.5. Scanner operator

Osoba na ulazu.

Mogućnosti:

- skenira QR/barcode,
- vidi rezultat validacije,
- eventualno ručno pretražuje posetioce,
- ne vidi finansijske podatke.

### 3.6. Buyer / Kupac

Osoba koja kupuje jednu ili više karata.

Kupac ne mora uvek biti isto što i posetilac.

Primer:

- Jedna osoba kupi 4 karte za društvo.
- Kupac je odgovoran za plaćanje.
- Posetioci mogu biti 4 različite osobe.

### 3.7. Attendee / Posetilac

Osoba koja dolazi na događaj i koristi kartu.

Podaci o posetiocu mogu biti:

- isti kao podaci kupca,
- različiti po svakoj karti,
- neobavezni,
- obavezni samo za određene kategorije karata.

### 3.8. Guest / VIP / Press

Poseban tip posetioca koji ne mora proći standardni checkout.

Može biti dodat ručno ili importovan.

---

## 4. Javno vidljive Entrio-like funkcionalnosti kao referentni model

Ovo je interpretacija javno dostupnih funkcionalnosti koje slične platforme nude, posebno Entrio, ali prevedena u naš sopstveni proizvodni model.

### 4.1. Event setup wizard

Sistem treba da ima wizard za kreiranje događaja.

Minimalni koraci:

1. Tip eventa:
   - plaćeni event,
   - besplatan event.
2. Format eventa:
   - fizički event,
   - online/virtualni event,
   - hybrid event kasnije.
3. Osnovni podaci:
   - naziv,
   - opis,
   - kategorija,
   - slika/banner,
   - datum početka,
   - datum završetka,
   - vreme otvaranja vrata,
   - lokacija.
4. Karte/registracije:
   - ticket categories,
   - cene,
   - kapaciteti,
   - prodajni periodi.
5. Podešavanja checkout-a:
   - payment methods,
   - custom fields,
   - buyer/attendee data rules.
6. Objavljivanje:
   - draft,
   - preview,
   - publish.

### 4.2. Ticketing

Sistem mora podržati:

- barcoded/QR digitalne karte,
- više kategorija karata,
- kapacitete po kategoriji,
- ukupni kapacitet eventa,
- sakrivene ticket type-ove za VIP/partnere,
- promocije i promo kodove,
- event page za prodaju,
- custom branding event stranice,
- export podataka posetilaca,
- import guest liste,
- API za attendee management u kasnijoj fazi.

### 4.3. Registration model za konferencije

Za konferencije i corporate evente, termin "ticket" može biti prikazan kao "registration".

Funkcionalno je isto:

- registration type = ticket type,
- attendee = participant,
- ticket = registration pass.

Razlika je u UI terminologiji i custom poljima.

Primeri registration kategorija:

- Early Bird,
- Regular,
- Student,
- Workshop,
- VIP,
- Speaker,
- Sponsor,
- Press.

### 4.4. Promo codes engine

Promo kodovi treba da podrže:

- procenat popusta,
- fiksni iznos popusta,
- 100% discount/free pass,
- ograničenje broja korišćenja,
- ograničenje po korisniku/emailu,
- ograničenje po ticket type-u,
- start/end datum,
- minimum order amount,
- max discount amount,
- tracking source,
- affiliate/referral tag,
- skriveni link koji automatski primenjuje promo kod.

### 4.5. Hidden ticket types

Hidden ticket type je kategorija karte koja nije javno prikazana na event page-u.

Primer:

- VIP Partner Pass,
- Speaker Pass,
- Sponsor Ticket,
- Internal Team,
- Press Accreditation,
- Invite-only.

Način pristupa:

- direktan secret link,
- promo kod,
- admin ručno dodaje,
- invite email.

### 4.6. Access control

Sistem treba da podrži:

- QR/barcode scanning,
- scanner app ili mobile web scanner,
- ručni check-in,
- guestlist check-in,
- multi-zone access kasnije,
- session attendance tracking kasnije,
- offline scanning kasnije,
- dashboard skeniranja u realnom vremenu.

### 4.7. Analytics

Organizator treba da vidi:

- broj prodatih karata po kategoriji,
- bruto prihod,
- neto prihod,
- service fee,
- payment provider fee,
- broj rezervisanih/neplaćenih/isteklih ordera,
- conversion rate,
- traffic sources,
- geo distribuciju kupaca,
- scan statistics,
- attendance rate,
- export u CSV/Excel.

### 4.8. On-site services

Kasnija faza:

- prodaja na ulazu,
- on-site blagajna,
- štampa karata,
- štampa akreditacija,
- najam skenera,
- osoblje za skeniranje,
- podrška na lokaciji.

### 4.9. Marketplace/discovery portal

Platforma može biti i javni portal događaja.

Funkcionalnosti:

- pretraga događaja,
- filteri po gradu,
- filteri po datumu,
- filteri po kategoriji,
- preporučeni događaji,
- istaknuti događaji,
- organizator profili,
- SEO landing pages za događaje.

---

## 5. Core domeni sistema

### 5.1. Identity & Access Management

Odgovornost:

- login,
- registracija,
- password reset,
- email verification,
- RBAC,
- organization membership,
- scanner operator access.

Entiteti:

- `users`,
- `organizations`,
- `organization_members`,
- `roles`,
- `permissions`,
- `invitations`,
- `api_keys`.

### 5.2. Organizer Management

Odgovornost:

- kreiranje organizacije,
- KYC/KYB podaci,
- bankovni račun,
- ugovorni fee model,
- verifikacioni status,
- pravna dokumentacija.

Statusi organizacije:

- `DRAFT`,
- `PENDING_VERIFICATION`,
- `VERIFIED`,
- `REJECTED`,
- `SUSPENDED`,
- `ARCHIVED`.

### 5.3. Event Management

Odgovornost:

- kreiranje eventa,
- editovanje eventa,
- objava eventa,
- lokacija,
- media,
- kategorije,
- SEO,
- publish workflow.

Statusi eventa:

- `DRAFT`,
- `PENDING_REVIEW`,
- `PUBLISHED`,
- `PRIVATE`,
- `SOLD_OUT`,
- `CANCELLED`,
- `POSTPONED`,
- `COMPLETED`,
- `ARCHIVED`.

### 5.4. Ticket Inventory Management

Odgovornost:

- ticket types,
- capacities,
- reservations,
- sold counts,
- availability,
- overbooking protection.

Ključna pravila:

- nikada ne prodati više od dozvoljenog kapaciteta,
- rezervacija traje ograničeno vreme,
- plaćanje mora potvrditi rezervaciju,
- expired rezervacija vraća kapacitet,
- refundacija može ili ne mora vratiti kapacitet, zavisno od pravila eventa.

### 5.5. Checkout & Orders

Odgovornost:

- cart,
- buyer data,
- attendee data,
- fee calculation,
- promo code application,
- order creation,
- order expiration,
- payment initialization,
- order confirmation.

Statusi ordera:

- `DRAFT`,
- `RESERVED`,
- `AWAITING_PAYMENT`,
- `PAID`,
- `PAYMENT_FAILED`,
- `EXPIRED`,
- `CANCELLED`,
- `PARTIALLY_REFUNDED`,
- `REFUNDED`.

### 5.6. Payment Management

Odgovornost:

- payment provider integration,
- payment initialization,
- callbacks/webhooks,
- idempotency,
- reconciliation,
- refund flow.

Payment methods:

- card,
- bank transfer,
- cash/on-site kasnije,
- free order,
- invoice payment za B2B kasnije.

Payment statusi:

- `INITIATED`,
- `REDIRECTED`,
- `AUTHORIZED`,
- `CAPTURED`,
- `FAILED`,
- `CANCELLED`,
- `EXPIRED`,
- `REFUND_PENDING`,
- `REFUNDED`,
- `UNKNOWN_REQUIRES_RECONCILIATION`.

### 5.7. Ticket Issuing

Odgovornost:

- generisanje ticket instance,
- QR/barcode generation,
- PDF generation,
- email delivery,
- wallet pass kasnije,
- ticket transfer kasnije.

Statusi karte:

- `ISSUED`,
- `DELIVERED`,
- `CHECKED_IN`,
- `VOIDED`,
- `REFUNDED`,
- `TRANSFERRED`,
- `LOST_REISSUED`.

### 5.8. Access Control

Odgovornost:

- scanning,
- validation,
- check-in,
- zone rules,
- scan logs,
- operator tracking.

Scan result statusi:

- `VALID_FIRST_ENTRY`,
- `VALID_REENTRY`,
- `ALREADY_USED`,
- `WRONG_EVENT`,
- `WRONG_ZONE`,
- `TICKET_CANCELLED`,
- `TICKET_REFUNDED`,
- `TICKET_NOT_ACTIVE`,
- `UNKNOWN_CODE`,
- `OFFLINE_ACCEPTED_PENDING_SYNC`,
- `OFFLINE_REJECTED`.

### 5.9. Forms & Custom Fields

Odgovornost:

- custom fields per event,
- field validation,
- conditional fields,
- buyer-level fields,
- attendee-level fields,
- export.

Field scopes:

- `PER_PURCHASE` - unosi se jednom za kupovinu,
- `PER_ATTENDEE` - unosi se za svaku kartu/posetioca,
- `PER_TICKET_TYPE` - samo za određene kategorije karata,
- `ADMIN_ONLY` - vidi/puni samo organizer/admin.

Field types:

- text,
- textarea,
- email,
- phone,
- number,
- date,
- dropdown,
- multi-select,
- checkbox,
- radio,
- country/city,
- company,
- VAT/PIB,
- file upload kasnije.

### 5.10. Reporting & Analytics

Odgovornost:

- sales dashboard,
- revenue dashboard,
- ticket type performance,
- promo performance,
- payment reports,
- attendee exports,
- scan reports,
- settlements.

### 5.11. Settlement & Payout

Odgovornost:

- obračun događaja,
- platform fee,
- payment fee,
- refunds,
- net amount,
- payout to organizer,
- payout status,
- settlement documents.

Payout statusi:

- `NOT_READY`,
- `READY_FOR_CALCULATION`,
- `CALCULATED`,
- `APPROVED`,
- `PAID_OUT`,
- `ON_HOLD`,
- `DISPUTED`.

### 5.12. Notifications

Odgovornost:

- transactional emails,
- ticket delivery,
- organizer alerts,
- payment failed emails,
- event reminders,
- cancellation/postponement notices,
- SMS/WhatsApp kasnije.

### 5.13. Platform Admin & Support

Odgovornost:

- global config,
- organizer verification,
- order lookup,
- resend ticket,
- manual refund request,
- fraud checks,
- audit.

---

## 6. Najvažniji feature: Ticket Types / Kategorije karata

Ovo je centralni deo sistema.

### 6.1. Ticket type koncept

Ticket type predstavlja kategoriju koju kupac bira na event page-u.

Primeri:

- Early Bird,
- Regular,
- VIP,
- Student,
- Group Ticket,
- Workshop Add-on,
- One Day Pass,
- Two Day Pass,
- Backstage,
- Press,
- Sponsor,
- Free Registration.

### 6.2. Obavezna polja za ticket type

Svaka kategorija karte treba da ima:

- `eventId`,
- `name`,
- `description`,
- `price`,
- `currency`,
- `capacity`,
- `minPerOrder`,
- `maxPerOrder`,
- `salesStartAt`,
- `salesEndAt`,
- `visibility`,
- `status`,
- `requiresAttendeeData`,
- `attendeeDataMode`,
- `sortOrder`.

### 6.3. Kapacitet po kategoriji

Organizator mora moći da kaže:

- Early Bird max 50,
- Regular max 200,
- VIP max 20,
- Student max 30.

Sistem mora voditi:

- ukupni kapacitet kategorije,
- broj prodatih,
- broj rezervisanih u aktivnim checkout sesijama,
- broj refundiranih,
- broj dostupnih.

Formula:

```text
available = capacity - soldCount - activeReservedCount + returnedToInventoryCount
```

### 6.4. Ukupan kapacitet eventa

Event može imati globalni kapacitet.

Primer:

- venue capacity = 300,
- ukupno po kategorijama je 350,
- sistem ne sme prodati više od 300 ako je global capacity active.

Business rule:

```text
Ako je event.globalCapacityEnabled = true, onda svaka rezervacija mora proveriti i ticket type capacity i event global capacity.
```

### 6.5. Prodajni periodi

Svaki ticket type ima svoj prodajni period:

- Early Bird: 01.08 - 31.08,
- Regular: 01.09 - 01.10,
- Last Minute: 02.10 - 09.10.

Moguća pravila:

- Regular se automatski aktivira kada Early Bird istekne.
- Regular se aktivira kada Early Bird bude sold out.
- Last Minute se aktivira X dana pre eventa.

### 6.6. Vidljivost ticket type-a

Vrednosti:

- `PUBLIC` - vidi se svima,
- `HIDDEN_LINK` - vidi se samo preko posebnog linka,
- `PROMO_CODE_ONLY` - vidi se tek kada se unese promo kod,
- `ADMIN_ONLY` - može ga izdati samo organizer/admin,
- `PASSWORD_PROTECTED` - vidljivo nakon lozinke.

### 6.7. Attendee data mode

Ovo je posebno bitno zbog tvoje ideje: jedna osoba može kupiti više karata, ali organizator može odlučiti da li traži podatke za svaku kartu.

Vrednosti:

- `BUYER_ONLY` - unose se samo podaci kupca; sve karte idu na kupca.
- `EACH_TICKET` - za svaku kartu se unose podaci posebnog posetioca.
- `OPTIONAL_EACH_TICKET` - sistem pita da li kupac želi da unese podatke za posetioce sada ili kasnije.
- `SAME_AS_BUYER_ALLOWED` - za svaku kartu postoje podaci, ali može se kliknuti "same as buyer".
- `NO_ATTENDEE_DATA` - nema dodatnih podataka, samo kupovina.

### 6.8. Custom fields po ticket type-u

Organizator može kreirati polja za event, ali može definisati gde se koriste.

Primer:

Polje: `Ime i prezime`

- scope: `PER_ATTENDEE`,
- required: true,
- ticket types: Early Bird, Regular, VIP.

Polje: `Kompanija`

- scope: `PER_ATTENDEE`,
- required: false,
- ticket types: Business, VIP.

Polje: `PIB firme za račun`

- scope: `PER_PURCHASE`,
- required: false,
- prikazuje se samo ako kupac čekira "Kupujem kao firma".

### 6.9. Group ticket

Group ticket može značiti dve stvari:

#### Model A: jedna karta važi za više osoba

Primer:

- Family ticket za 4 osobe,
- jedan QR kod,
- scanner označava ulaz za 4 osobe.

Polja:

- `admissionQuantity = 4`,
- `ticketInstancesGenerated = 1`,
- `attendeeSlots = 4`.

#### Model B: paket generiše više individualnih karata

Primer:

- Group 5-pack,
- kupac kupuje jedan proizvod,
- sistem generiše 5 odvojenih karata.

Polja:

- `bundleSize = 5`,
- `ticketInstancesGenerated = 5`,
- svaki ticket ima svoj QR.

Za MVP preporuka: podržati prvo standardni model gde quantity = broj karata. Group bundle ostaviti za kasnije.

---

## 7. Checkout flow

### 7.1. Standardni tok kupovine

1. Kupac otvara event page.
2. Vidi dostupne ticket type-ove.
3. Bira količinu po ticket type-u.
4. Klikne "nastavi".
5. Sistem kreira privremenu rezervaciju kapaciteta.
6. Kupac unosi buyer podatke.
7. Ako event zahteva attendee podatke, kupac unosi podatke po karti.
8. Kupac unosi promo kod ako ga ima.
9. Sistem računa total.
10. Kupac bira payment method.
11. Sistem kreira order.
12. Kupac plaća.
13. Payment webhook potvrđuje plaćanje.
14. Sistem izdaje karte.
15. Sistem šalje email sa kartama.
16. Organizator vidi prodaju u dashboard-u.

### 7.2. Reservation hold

Da bi se sprečio overselling, checkout mora da rezerviše kapacitet na ograničeno vreme.

Preporuka:

- reservation TTL: 10-15 minuta,
- nakon isteka kapacitet se oslobađa,
- frontend prikazuje countdown.

Statusi rezervacije:

- `ACTIVE`,
- `COMPLETED`,
- `EXPIRED`,
- `CANCELLED`.

### 7.3. Checkout bez plaćanja

Za besplatne evente ili 100% promo code:

1. Kupac bira free ticket.
2. Unosi potrebne podatke.
3. Sistem kreira order sa totalAmount = 0.
4. Payment se preskače.
5. Karte se izdaju odmah.

### 7.4. Bank transfer flow

Kasnija faza ili B2B model.

Tok:

1. Kupac bira bank transfer.
2. Sistem kreira order `AWAITING_PAYMENT`.
3. Sistem generiše instrukcije za uplatu.
4. Karte se ne izdaju dok uplata nije potvrđena, osim ako organizator dozvoli manual approval.
5. Admin/automatika potvrđuje uplatu.
6. Karte se izdaju.

### 7.5. Payment callback idempotency

Payment callback može stići više puta.

Pravilo:

- Payment provider transaction ID mora biti unique.
- Callback se čuva u `payment_webhook_events`.
- Ako je već obrađen, ne sme ponovo izdati karte.
- Ticket issuance mora biti idempotentno.

---

## 8. Ticket lifecycle

### 8.1. Kreiranje karte

Karta se kreira tek kada je order plaćen ili potvrđen kao free.

Svaka karta mora imati:

- unique ticket code,
- QR/barcode value,
- event ID,
- ticket type ID,
- order ID,
- buyer ID ili buyer email,
- attendee data ako postoji,
- status,
- issuedAt,
- optional seat ID.

### 8.2. QR code pravilo

QR ne treba da sadrži sve podatke o karti.

Preporuka:

```text
QR payload = opaque secure token / ticket code
```

Sistem na scan-u radi lookup.

Primer:

```text
PLF-2026-GOC-8K2J9S1Q
```

Za dodatnu sigurnost:

- token dovoljno dug i nepredvidiv,
- signed payload opcionalno,
- QR može imati checksum/signature,
- ne koristiti sequential ID kao QR vrednost.

### 8.3. PDF karta

PDF karta treba da sadrži:

- naziv događaja,
- datum i vreme,
- lokaciju,
- ticket type,
- ime posetioca ako postoji,
- buyer/order reference,
- QR kod,
- osnovna pravila ulaza,
- kontakt support.

### 8.4. Email delivery

Emailovi:

- order confirmation,
- ticket delivery,
- payment failed,
- event reminder,
- event cancelled,
- event postponed,
- refund confirmation.

### 8.5. Ticket transfer kasnije

Kupac može preneti kartu na drugu osobu ako organizator dozvoli.

Pravila:

- transfer enabled/disabled per event,
- transfer deadline,
- old QR se poništava,
- novi QR se generiše,
- audit log obavezan.

---

## 9. Access control / scanner

### 9.1. MVP scanner

Prva verzija može biti mobile web app:

- login za scanner operatora,
- izbor eventa,
- kamera skenira QR,
- backend validira kartu,
- ekran prikazuje rezultat.

Rezultat treba da bude jasan:

- zeleno: validna karta,
- crveno: nevažeća karta,
- žuto: već iskorišćena / upozorenje,
- sivo: nema konekcije / offline pending.

### 9.2. Pravila validacije

Karta je validna ako:

- postoji,
- pripada eventu,
- status je `ISSUED` ili drugi dozvoljen,
- nije refundirana,
- nije poništena,
- nije već check-in ako re-entry nije dozvoljen,
- ticket type je dozvoljen za zonu,
- event je aktivan za scanning.

### 9.3. Multi-zone access

Kasnija faza.

Primer zona:

- main entrance,
- VIP area,
- backstage,
- workshop room,
- conference hall A,
- conference hall B.

Ticket type može imati dozvoljene zone.

Primer:

- Regular: main entrance + main hall,
- VIP: main entrance + VIP area + main hall,
- Speaker: all zones,
- Workshop: workshop room only.

### 9.4. Offline scanning

Kasnija faza, ali bitna za ozbiljne evente.

Problem:

- internet na ulazu može biti loš,
- scanner mora raditi i offline.

Model:

- pre eventa se sinhronizuje lista validnih ticket tokena,
- scan se lokalno upisuje,
- kada se internet vrati, scan log se syncuje,
- konflikt se rešava pravilima.

Konflikt primer:

- isti QR skeniran na dva uređaja offline.
- prvi sync koji stigne postaje validan,
- drugi dobija conflict/already used.

Za MVP se može odložiti offline mode.

---

## 10. Event page / Public marketplace

### 10.1. Event page elementi

Event page treba da ima:

- hero image/banner,
- naziv događaja,
- datum i vreme,
- lokacija/mapa,
- organizator,
- opis,
- agenda/program,
- speakers/performers kasnije,
- ticket selector,
- FAQ,
- refund policy,
- share buttons,
- related events.

### 10.2. SEO

Za svaki public event generisati:

- slug,
- meta title,
- meta description,
- Open Graph image,
- structured data za event,
- canonical URL.

### 10.3. Discovery portal

Javni portal treba da podrži:

- listu događaja,
- search,
- filter po gradu,
- filter po datumu,
- filter po kategoriji,
- "ovaj vikend",
- "danas",
- "sutra",
- featured events,
- popular events.

---

## 11. Admin panel za organizatora

### 11.1. Organizer dashboard

Dashboard treba da prikaže:

- aktivne evente,
- draft evente,
- ukupan prihod,
- broj prodatih karata,
- broj check-in posetilaca,
- pending payouts,
- zadnje order-e,
- upozorenja.

### 11.2. Event dashboard

Za svaki event:

- total sold,
- total revenue,
- sold by ticket type,
- capacity usage,
- sales timeline,
- orders list,
- attendee list,
- promo code usage,
- scan stats,
- export dugmad.

### 11.3. Ticket type management UI

UI za ticket type treba da podrži:

- add ticket type,
- duplicate ticket type,
- reorder,
- enable/disable,
- set price,
- set capacity,
- set sale period,
- set min/max per order,
- set visibility,
- set custom attendee data behavior.

### 11.4. Attendee management

Organizator treba da može:

- pretražuje posetioce,
- vidi attendee details,
- menja podatke ako je dozvoljeno,
- resend ticket,
- manual check-in,
- export,
- import guest list.

### 11.5. Orders management

Organizator/admin treba da vidi:

- order number,
- buyer,
- amount,
- status,
- payment method,
- tickets,
- createdAt,
- paidAt,
- refund status.

Akcije:

- resend confirmation,
- resend tickets,
- cancel unpaid order,
- request refund,
- manual mark as paid ako je bank transfer/manual flow.

---

## 12. Platform admin panel

Super admin panel treba da podrži:

- pregled svih organizatora,
- verifikaciju organizatora,
- pregled svih događaja,
- moderation publish flow,
- payment provider config,
- fee config,
- payout config,
- order lookup,
- ticket lookup,
- refund management,
- support tools,
- audit logs,
- system health.

---

## 13. Custom forms detaljno

### 13.1. Zašto je ovo ključno

Različiti događaji traže različite podatke:

- konferencija: ime, prezime, firma, pozicija, email,
- festival: ime, prezime, datum rođenja,
- workshop: nivo znanja, laptop requirement,
- sportski događaj: klub, kategorija,
- B2B event: company, PIB, invoice data.

### 13.2. Form builder

Organizator može dodati polja:

- label,
- type,
- required,
- placeholder,
- help text,
- validation,
- options,
- default value,
- visible for ticket types,
- scope.

### 13.3. Per purchase vs per attendee

#### Per purchase

Jednom po kupovini.

Primer:

- buyer email,
- phone,
- invoice company,
- billing address.

#### Per attendee

Za svaku kartu.

Primer:

Kupac kupuje 3 karte.

Sistem prikazuje:

- Posetilac 1: ime, prezime, email,
- Posetilac 2: ime, prezime, email,
- Posetilac 3: ime, prezime, email.

### 13.4. Conditional fields

Kasnija faza.

Primer:

Ako kupac odabere "Kupujem kao firma", prikaži:

- naziv firme,
- PIB,
- adresa,
- email za račun.

Ako ticket type = Student, prikaži:

- fakultet,
- broj indeksa,
- upload potvrde kasnije.

---

## 14. Promo kodovi detaljno

### 14.1. Promo code entitet

Polja:

- code,
- name,
- description,
- discountType,
- discountValue,
- maxDiscountAmount,
- usageLimitTotal,
- usageLimitPerEmail,
- startsAt,
- endsAt,
- appliesToTicketTypes,
- minimumQuantity,
- minimumAmount,
- active,
- sourceTag.

### 14.2. Discount types

- `PERCENTAGE`,
- `FIXED_AMOUNT`,
- `FREE_TICKET`,
- `SET_PRICE`,
- `BUY_X_GET_Y` kasnije.

### 14.3. Promo code validation

Promo kod je validan ako:

- postoji,
- aktivan je,
- nije istekao,
- još ima dostupnih korišćenja,
- važi za izabrani ticket type,
- order zadovoljava minimum rules,
- kupac nije već potrošio limit.

---

## 15. Seating plan

Seating plan je napredni modul.

### 15.1. MVP odluka

Za prvu verziju ne mora biti implementiran vizuelni seating editor.

Dovoljno za MVP:

- general admission,
- ticket category capacity.

### 15.2. Faza 2 seating

Podržati:

- sekcije,
- redove,
- sedišta,
- seat map upload/import,
- seat selection UI,
- seat reservation hold,
- seat pricing by section,
- blocked seats,
- accessible seats.

Entiteti:

- `venues`,
- `venue_sections`,
- `seat_rows`,
- `seats`,
- `event_seat_maps`,
- `seat_reservations`.

---

## 16. Refunds, cancellation, postponement

### 16.1. Refund policy

Refund pravila mogu biti:

- no refund,
- refund until X days before event,
- organizer approval required,
- full refund,
- partial refund,
- platform fee refundable/non-refundable.

### 16.2. Cancellation flow

Ako se event otkaže:

1. Event status -> `CANCELLED`.
2. Prodaja se zaustavlja.
3. Checkout rezervacije se gase.
4. Kupci se obaveštavaju.
5. Refund workflow se pokreće prema policy-ju.
6. Settlement se stavlja `ON_HOLD`.

### 16.3. Postponement flow

Ako se event pomeri:

1. Event status -> `POSTPONED`.
2. Novi datum se unosi.
3. Kupci se obaveštavaju.
4. Karta ostaje važeća ili se nudi refund, zavisno od pravila.

---

## 17. Payout / obračun organizatoru

### 17.1. Obračun

Za svaki event sistem računa:

```text
grossSales
- refunds
- platformFee
- paymentProcessingFee
- chargebacks
- manualAdjustments
= netPayout
```

### 17.2. Kada se radi payout

Opcije:

- nakon završetka eventa,
- X dana nakon eventa,
- periodično za proverene organizatore,
- advance payout za enterprise organizatore kasnije.

Za početak je sigurnije:

- payout nakon eventa,
- posle perioda za reklamacije/refundacije,
- uz manual approval.

### 17.3. Settlement dokument

Organizator dobija:

- event summary,
- broj prodatih karata po kategoriji,
- bruto iznos,
- refundacije,
- fee platforme,
- fee payment providera,
- neto iznos za isplatu,
- datum isplate,
- račun/platform invoice ako je potrebno.

---

## 18. Fiskalizacija, porezi i lokalizacija

Ovaj deo mora biti proveravan sa knjigovođom/pravnikom za svako tržište.

### 18.1. Srbija - otvorena pitanja

Potrebno definisati:

- ko je prodavac karte prema kupcu,
- da li platforma prodaje u ime i za račun organizatora,
- da li platforma naplaćuje samo uslugu posredovanja,
- ko izdaje fiskalni račun kupcu,
- da li se fiskalizacija radi na nivou organizatora ili platforme,
- kako se tretiraju udruženja,
- da li postoje posebna pravila za kulturne/sportske događaje,
- PDV tretman po tipu organizatora i događaja,
- model ugovora sa organizatorom.

### 18.2. Konfiguracija po državi

Sistem treba projektovati tako da podrži:

- currency,
- tax rules,
- invoice rules,
- fiscalization provider,
- payment providers,
- language,
- terms and conditions,
- privacy policy.

### 18.3. Dokumenti

Potrebni dokumenti:

- Terms of Service za kupce,
- Organizer Terms,
- Privacy Policy,
- Data Processing Agreement,
- Refund Policy template,
- Organizer contract,
- Payment/settlement policy.

---

## 19. Arhitektura - preporuka

### 19.1. Pristup za početak

Preporuka je modularni monolit, ne odmah mikroservisi.

Razlog:

- brži razvoj,
- manje DevOps kompleksnosti,
- lakše transakcije za inventory/order/payment,
- jednostavniji deployment,
- dovoljno za MVP i rani rast.

Tehnologije:

- Java 21,
- Spring Boot 3,
- PostgreSQL,
- Redis za lock/session/cache/rate limit,
- S3 compatible storage za PDF/ticket assets,
- Flyway/Liquibase,
- Keycloak ili Spring Security auth,
- React/Vue/Angular frontend,
- mobile web scanner za MVP,
- Docker Compose za local,
- Kubernetes kasnije.

### 19.2. Modularni monolit moduli

Predlog modula:

```text
app
 ├── identity
 ├── organizations
 ├── events
 ├── ticketing
 ├── inventory
 ├── checkout
 ├── payments
 ├── orders
 ├── tickets
 ├── accesscontrol
 ├── promotions
 ├── forms
 ├── notifications
 ├── reporting
 ├── settlements
 ├── admin
 └── shared
```

### 19.3. Kasnija mikroservis podela

Ako sistem poraste, mogu se izdvojiti:

- identity-service,
- event-service,
- order-service,
- payment-service,
- ticket-service,
- access-control-service,
- notification-service,
- reporting-service,
- settlement-service.

Ali za MVP ne komplikovati.

### 19.4. C4 Context - tekstualno

```text
Buyer -> Public Web App -> Backend API -> DB
Organizer -> Organizer Admin App -> Backend API -> DB
Scanner Operator -> Scanner Web/Mobile App -> Backend API -> DB
Payment Provider -> Payment Webhook -> Backend API -> DB
Email Provider <- Notification Module <- Backend API
Platform Admin -> Admin Panel -> Backend API
```

### 19.5. C4 Container - tekstualno

```text
[Public Frontend]
  - event discovery
  - event page
  - checkout

[Organizer Admin Frontend]
  - event management
  - ticket management
  - orders
  - reports

[Scanner App]
  - QR scan
  - ticket validation
  - check-in

[Backend API]
  - modular monolith
  - REST API
  - business logic

[PostgreSQL]
  - transactional source of truth

[Redis]
  - reservation locks
  - short-lived checkout sessions
  - rate limiting

[Object Storage]
  - generated PDF tickets
  - event images
  - exports

[Payment Provider]
  - card payments
  - webhooks

[Email Provider]
  - transactional emails
```

---

## 20. Baza - high-level schema

### 20.1. Identity

```text
users
- id
- email
- password_hash / external_id
- first_name
- last_name
- phone
- status
- created_at
- updated_at

organizations
- id
- name
- legal_name
- tax_id
- registration_number
- address
- country
- bank_account
- verification_status
- fee_model_id
- created_at
- updated_at

organization_members
- id
- organization_id
- user_id
- role
- status
- invited_by
- created_at
- updated_at
```

### 20.2. Events

```text
events
- id
- organization_id
- name
- slug
- description
- category
- event_type
- status
- starts_at
- ends_at
- doors_open_at
- timezone
- location_id
- global_capacity_enabled
- global_capacity
- currency
- cover_image_url
- published_at
- created_by
- updated_by
- created_at
- updated_at

event_locations
- id
- event_id
- name
- address
- city
- country
- latitude
- longitude
```

### 20.3. Ticket types

```text
ticket_types
- id
- event_id
- name
- description
- price_amount
- currency
- capacity
- min_per_order
- max_per_order
- sales_start_at
- sales_end_at
- visibility
- status
- requires_attendee_data
- attendee_data_mode
- sort_order
- created_at
- updated_at
```

### 20.4. Inventory

```text
ticket_inventory
- id
- ticket_type_id
- capacity
- sold_count
- reserved_count
- refunded_count
- version
- updated_at

reservations
- id
- event_id
- order_id nullable
- session_id
- status
- expires_at
- created_at

reservation_items
- id
- reservation_id
- ticket_type_id
- quantity
```

### 20.5. Forms

```text
event_forms
- id
- event_id
- name
- status

form_fields
- id
- event_form_id
- key
- label
- type
- scope
- required
- options_json
- validation_json
- visible_for_ticket_types_json
- sort_order

field_values
- id
- field_id
- order_id nullable
- attendee_id nullable
- value_text
- value_json
```

### 20.6. Orders and payments

```text
orders
- id
- order_number
- event_id
- buyer_email
- buyer_first_name
- buyer_last_name
- buyer_phone
- status
- currency
- subtotal_amount
- discount_amount
- service_fee_amount
- payment_fee_amount
- total_amount
- reservation_id
- promo_code_id nullable
- created_at
- paid_at
- expires_at

order_items
- id
- order_id
- ticket_type_id
- quantity
- unit_price_amount
- total_price_amount
- discount_amount

payments
- id
- order_id
- provider
- provider_transaction_id
- method
- status
- amount
- currency
- redirect_url
- raw_response_json
- created_at
- updated_at

payment_webhook_events
- id
- provider
- provider_event_id
- provider_transaction_id
- payload_json
- processed
- processed_at
- created_at
```

### 20.7. Attendees and tickets

```text
attendees
- id
- order_id
- ticket_type_id
- first_name
- last_name
- email
- phone
- company
- metadata_json
- created_at
- updated_at

tickets
- id
- event_id
- order_id
- order_item_id
- attendee_id nullable
- ticket_type_id
- ticket_code
- qr_token_hash
- status
- issued_at
- delivered_at
- checked_in_at nullable
- pdf_url
- created_at
- updated_at
```

### 20.8. Promo codes

```text
promo_codes
- id
- event_id
- code
- name
- discount_type
- discount_value
- max_discount_amount
- usage_limit_total
- usage_limit_per_email
- starts_at
- ends_at
- active
- source_tag
- created_at
- updated_at

promo_code_ticket_types
- promo_code_id
- ticket_type_id

promo_code_redemptions
- id
- promo_code_id
- order_id
- buyer_email
- discount_amount
- created_at
```

### 20.9. Access control

```text
scanner_devices
- id
- event_id
- name
- operator_user_id
- status
- last_seen_at

access_zones
- id
- event_id
- name
- description

access_zone_ticket_types
- zone_id
- ticket_type_id

scan_logs
- id
- event_id
- ticket_id nullable
- ticket_code
- scanner_device_id
- operator_user_id
- zone_id nullable
- result
- message
- scanned_at
- sync_status
```

### 20.10. Settlement

```text
settlements
- id
- event_id
- organization_id
- status
- gross_amount
- refund_amount
- platform_fee_amount
- payment_fee_amount
- net_payout_amount
- currency
- calculated_at
- approved_at
- paid_out_at

settlement_items
- id
- settlement_id
- type
- description
- amount
- reference_id
```

---

## 21. API predlog

### 21.1. Public API

```http
GET    /api/public/events
GET    /api/public/events/{slug}
GET    /api/public/events/{eventId}/ticket-types
POST   /api/public/checkout/reservations
GET    /api/public/checkout/reservations/{reservationId}
POST   /api/public/checkout/orders
POST   /api/public/checkout/orders/{orderId}/pay
GET    /api/public/orders/{orderNumber}
POST   /api/public/promo-codes/validate
```

### 21.2. Organizer API

```http
GET    /api/organizer/events
POST   /api/organizer/events
GET    /api/organizer/events/{eventId}
PUT    /api/organizer/events/{eventId}
POST   /api/organizer/events/{eventId}/publish
POST   /api/organizer/events/{eventId}/cancel

GET    /api/organizer/events/{eventId}/ticket-types
POST   /api/organizer/events/{eventId}/ticket-types
PUT    /api/organizer/ticket-types/{ticketTypeId}
DELETE /api/organizer/ticket-types/{ticketTypeId}

GET    /api/organizer/events/{eventId}/orders
GET    /api/organizer/events/{eventId}/attendees
GET    /api/organizer/events/{eventId}/tickets
POST   /api/organizer/tickets/{ticketId}/resend

GET    /api/organizer/events/{eventId}/reports/sales
GET    /api/organizer/events/{eventId}/reports/scans
GET    /api/organizer/events/{eventId}/export/attendees
```

### 21.3. Scanner API

```http
GET    /api/scanner/events
POST   /api/scanner/events/{eventId}/scan
GET    /api/scanner/events/{eventId}/stats
```

### 21.4. Payment API

```http
POST   /api/payments/{provider}/webhook
GET    /api/payments/orders/{orderId}/status
POST   /api/admin/orders/{orderId}/refund
```

### 21.5. Admin API

```http
GET    /api/admin/organizations
POST   /api/admin/organizations/{organizationId}/verify
GET    /api/admin/events
POST   /api/admin/events/{eventId}/approve
GET    /api/admin/orders
GET    /api/admin/tickets/{ticketCode}
GET    /api/admin/settlements
POST   /api/admin/settlements/{settlementId}/approve
POST   /api/admin/settlements/{settlementId}/mark-paid
```

---

## 22. Concurrency i overbooking protection

### 22.1. Najveći rizik

Najveći tehnički rizik ticketing sistema je da se proda više karata nego što postoji kapacitet.

### 22.2. Preporučeni pristup

Koristiti DB transakciju i optimistic/pessimistic locking.

Opcija A: Pessimistic lock

```sql
SELECT * FROM ticket_inventory WHERE ticket_type_id = ? FOR UPDATE;
```

Tok:

1. Otvori transakciju.
2. Zaključaj inventory row.
3. Proveri available.
4. Povećaj reserved_count.
5. Kreiraj reservation.
6. Commit.

Opcija B: Atomic update

```sql
UPDATE ticket_inventory
SET reserved_count = reserved_count + :qty,
    version = version + 1
WHERE ticket_type_id = :id
  AND capacity - sold_count - reserved_count >= :qty;
```

Ako update count = 0, nema dovoljno karata.

### 22.3. Reservation expiration job

Scheduler mora redovno čistiti expired reservations.

Tok:

1. Nađe active reservations gde `expires_at < now()`.
2. Zaključa reservation.
3. Smanji reserved_count.
4. Postavi status `EXPIRED`.
5. Ako postoji order, order -> `EXPIRED`.

### 22.4. Payment success nakon isteka

Moguće je da payment provider vrati success kasno.

Pravilo:

- Ako je order expired, ali payment success stiže, sistem mora odlučiti:
  - ako još ima kapaciteta, potvrdi order,
  - ako nema kapaciteta, stavi u `UNKNOWN_REQUIRES_RECONCILIATION` i pokreni refund/manual support.

Za MVP preporuka:

- checkout TTL dovoljno dug,
- payment status proveravati pre expire,
- edge case rešavati support/manual.

---

## 23. Security

### 23.1. Osnovno

- HTTPS svuda.
- Password hashing ako se ne koristi external identity.
- JWT/session security.
- RBAC na svakom endpointu.
- Organization-level authorization.
- Rate limiting za checkout i scanner.
- Audit log za finansijske i ticket operacije.

### 23.2. QR sigurnost

- QR token ne sme biti običan DB ID.
- Koristiti random token.
- U bazi čuvati hash tokena, ne raw token ako je moguće.
- Scan endpoint mora imati rate limit.

### 23.3. Payment sigurnost

- Webhook signature verification.
- Idempotency key.
- Ne verovati frontendu da je payment uspešan.
- Order se potvrđuje samo na osnovu backend provider callback/provere.

### 23.4. GDPR / privacy

- Prikupljati minimalne potrebne podatke.
- Objasniti organizatoru da je odgovoran za custom polja koja traži.
- Omogućiti data export.
- Definisati retention period.
- Omogućiti brisanje/anonymization gde zakonski moguće.

---

## 24. Observability

Logovati:

- order created,
- reservation created/expired,
- payment callback received,
- payment status changed,
- tickets issued,
- email sent/failed,
- scan result,
- refund requested/completed,
- settlement calculated.

Metrici:

- checkout conversion,
- payment success rate,
- email delivery rate,
- scan latency,
- API error rate,
- active reservations,
- sold tickets per minute,
- webhook processing lag.

Alerts:

- payment webhook failures,
- ticket issuance failures,
- email provider failures,
- high scan error rate,
- inventory inconsistency,
- DB lock timeout.

---

## 25. MVP scope

### 25.1. MVP mora imati

1. Auth za organizer/admin.
2. Organizacija i organizer profil.
3. Kreiranje eventa.
4. Public event page.
5. Ticket type management.
6. Kapacitet po ticket type-u.
7. Cena po ticket type-u.
8. Min/max per order.
9. Sales start/end.
10. Public/hidden ticket visibility.
11. Buyer data.
12. Attendee data per ticket ili buyer-only.
13. Custom fields basic.
14. Checkout reservation.
15. Free order flow.
16. Card payment integration ili mock adapter spreman za provider.
17. Payment webhook handling.
18. Ticket issuance.
19. QR/PDF ticket generation.
20. Email delivery.
21. Organizer dashboard basic.
22. Orders list.
23. Attendees list.
24. CSV export.
25. Scanner web app.
26. Scan logs.
27. Platform admin minimal.
28. Audit log.

### 25.2. MVP ne mora imati

- complex seating plan,
- offline scanner,
- multi-zone access,
- wallet pass,
- affiliate system,
- full marketplace,
- on-site POS,
- bank transfer automation,
- fiscalization automation,
- mobile native app,
- complex refund automation.

---

## 26. Faza 2

Dodati:

- promo codes advanced,
- marketplace discovery,
- advanced analytics,
- Mailchimp/GA/Meta Pixel integration,
- guestlist import,
- manual ticket issuing,
- press/VIP badges,
- on-site check-in edit,
- basic seating plan,
- organizer team roles,
- refund workflow,
- settlement module,
- bank transfer support,
- invoice/fiscalization integration.

---

## 27. Faza 3

Dodati:

- multi-zone access,
- offline scanner,
- native scanner app,
- API za velike organizatore,
- white-label event pages,
- custom domains,
- virtual/hybrid platform,
- sponsor/exhibitor modules,
- advanced referral engine,
- POS/on-site box office,
- outlet/reseller network,
- AI recommendations za event discovery,
- fraud detection.

---

## 28. User stories

### 28.1. Organizer - event creation

Kao organizator, želim da kreiram događaj sa nazivom, opisom, datumom, lokacijom i slikom, kako bih mogao da ga objavim i prodajem karte.

Acceptance criteria:

- organizer može kreirati draft event,
- required polja se validiraju,
- event dobija unique slug,
- event nije javno vidljiv dok se ne publishuje.

### 28.2. Organizer - ticket categories

Kao organizator, želim da kreiram više kategorija karata sa različitim cenama i kapacitetima, kako bih mogao da prodajem Early Bird, Regular i VIP karte.

Acceptance criteria:

- mogu dodati više ticket type-ova,
- svaki ima cenu,
- svaki ima kapacitet,
- mogu definisati sales start/end,
- kupac vidi samo aktivne i javne ticket type-ove,
- sistem ne dozvoljava prodaju preko kapaciteta.

### 28.3. Organizer - attendee data per ticket

Kao organizator, želim da definišem da li kupac unosi podatke samo za sebe ili za svakog posetioca posebno, kako bih prikupio tačne podatke za događaj.

Acceptance criteria:

- event/ticket type ima attendeeDataMode,
- ako je `BUYER_ONLY`, forma se prikazuje jednom,
- ako je `EACH_TICKET`, forma se prikazuje za svaku kartu,
- custom fields se čuvaju i mogu se exportovati.

### 28.4. Buyer - buy tickets

Kao kupac, želim da brzo kupim karte i dobijem ih emailom.

Acceptance criteria:

- kupac bira ticket type i quantity,
- sistem rezerviše kapacitet,
- kupac unosi podatke,
- kupac plaća,
- nakon uspešnog plaćanja dobija email sa kartama,
- karte imaju QR kod.

### 28.5. Scanner operator - scan ticket

Kao osoba na ulazu, želim da skeniram QR kod i odmah vidim da li je karta validna.

Acceptance criteria:

- scanner operator može izabrati event,
- kamera skenira QR,
- backend vraća rezultat,
- validna karta se označava kao checked-in,
- ista karta drugi put prikazuje upozorenje.

---

## 29. AI implementation backlog

### Epic 1: Project foundation

- setup Spring Boot project,
- setup modules/packages,
- setup PostgreSQL,
- setup Flyway/Liquibase,
- setup auth/security,
- setup base audit columns,
- setup exception handling,
- setup API response model.

### Epic 2: Organization and users

- organization entity,
- organization member entity,
- roles,
- invite member,
- organization context resolver.

### Epic 3: Events

- event entity,
- create/update event,
- publish/unpublish,
- public event page API,
- event image upload.

### Epic 4: Ticket types and inventory

- ticket type entity,
- ticket inventory entity,
- create/update ticket type,
- capacity rules,
- availability API,
- reservation engine.

### Epic 5: Checkout

- reservation API,
- order entity,
- order item entity,
- buyer data,
- attendee data,
- custom fields,
- free order completion.

### Epic 6: Payments

- payment adapter interface,
- mock payment provider,
- real provider integration,
- webhook endpoint,
- idempotency,
- payment/order state machine.

### Epic 7: Ticket issuing

- ticket entity,
- QR token generation,
- PDF generation,
- email delivery,
- resend ticket.

### Epic 8: Scanner

- scanner auth/role,
- scan endpoint,
- validation service,
- scan log,
- scanner stats.

### Epic 9: Reporting

- sales dashboard API,
- ticket type report,
- attendee export,
- scan report.

### Epic 10: Admin/support

- organization verification,
- global event list,
- order lookup,
- ticket lookup,
- audit log view.

---

## 30. Coding rules za AI agenta

### 30.1. Backend rules

- Koristiti jasne domenske servise.
- Ne stavljati poslovnu logiku u controller.
- Svaka promena statusa mora ići kroz service/state transition metodu.
- Za inventory koristiti transakcije.
- Za payment webhook koristiti idempotency.
- Za scan koristiti audit/scan log.
- Za svaki public endpoint razmisliti o rate limitu.

### 30.2. Naming

Koristiti engleske nazive u kodu:

- Event,
- TicketType,
- Ticket,
- Order,
- Payment,
- Attendee,
- Organization,
- Reservation,
- PromoCode,
- ScanLog,
- Settlement.

UI može biti lokalizovan na srpski/engleski.

### 30.3. Testovi

Obavezni testovi:

- ne može prodati preko capacity,
- expired reservation vraća kapacitet,
- payment webhook je idempotentan,
- ticket issuing se ne duplira,
- already scanned ticket vraća warning,
- hidden ticket nije prikazan javno,
- promo code limit radi,
- attendee fields per ticket se pravilno čuvaju.

---

## 31. Ključne poslovne odluke koje treba doneti pre produkcije

1. Da li platforma prodaje u ime i za račun organizatora ili kao reseller?
2. Ko izdaje fiskalni račun kupcu u Srbiji?
3. Da li servisnu naknadu plaća kupac ili organizator?
4. Kada se radi payout organizatoru?
5. Da li payout ide pre ili posle eventa?
6. Da li su refundacije automatske ili manual approval?
7. Da li fizička lica mogu organizovati događaje ili samo pravna lica/udruženja/preduzetnici?
8. Ko je data controller za attendee podatke?
9. Da li platforma dozvoljava evente koji zahtevaju posebne dozvole?
10. Koji payment provider se koristi u Srbiji?

---

## 32. Predlog prioriteta za razvoj

### Sprint 1

- domain model,
- DB migrations,
- organization,
- event CRUD,
- ticket type CRUD.

### Sprint 2

- inventory,
- reservation,
- public event page API,
- checkout draft.

### Sprint 3

- order,
- custom fields basic,
- attendee data modes,
- free order complete.

### Sprint 4

- payment mock/real provider,
- webhook,
- ticket issuing,
- QR generation.

### Sprint 5

- PDF/email,
- organizer dashboard,
- orders/attendees export.

### Sprint 6

- scanner app/API,
- scan logs,
- support tools,
- hardening.

---

## 33. Definition of Done za MVP

MVP je spreman kada:

- organizator može napraviti event,
- može kreirati više ticket category-ja,
- može definisati cenu i kapacitet po kategoriji,
- može definisati buyer-only ili per-ticket attendee podatke,
- kupac može kupiti kartu,
- sistem ne overselluje,
- payment success izdaje karte,
- kupac dobija email/PDF/QR,
- scanner može validirati kartu,
- organizator vidi prodaju i attendee listu,
- podaci mogu da se izvezu,
- super admin može rešiti osnovne support slučajeve.

---

## 34. Najveće tehničke zamke

1. Overselling zbog lošeg inventory lock-a.
2. Dupliranje karata zbog payment webhook retry-ja.
3. Nejasan odnos buyer vs attendee.
4. Custom fields bez dobrog scope modela.
5. Scanner koji zavisi od lošeg interneta.
6. Refund bez jasnog stanja order/payment/ticket.
7. Payout pre rešavanja refundacija i chargebackova.
8. Loš audit trail za finansijske akcije.
9. Pomešani podaci više organizatora.
10. Nejasna fiskalna odgovornost.

---

## 35. Kratka produkt mantra

Passlify treba da bude:

- jednostavan za organizatora,
- brz za kupca,
- siguran za plaćanja,
- precizan za kapacitete,
- pouzdan na ulazu,
- transparentan za obračune,
- spreman za lokalne pravne i fiskalne razlike.

