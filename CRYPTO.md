Some notes on US crypto laws when publishing on the Play Store
==============================================================

Basically, use of SSL, TLS, SSH or any other crypto protocol, even widely used
or publicly accessible, is subject to US crypto laws.

For _free of charge, open-source software_, a notification must be sent to
crypt@bis.doc.gov and enc@nsa.gov each time the cryptography components change.

[This answer](http://stackoverflow.com/questions/2325452) seems to indicate
that you do not need to wait for an answer. I acted upon it, but I could be wrong there.

---------------------------------------------------------------------

_(From [this StackOverflow answer](http://stackoverflow.com/questions/2135081/does-my-application-contain-encryption))_

Unfortunately, I believe that your app "contains encryption" in terms of US BIS
even if you just use HTTPS (if your app is not an exception included in
question 2).

Quote from FAQ on iTunes Connect:

"How do I know if I can follow the Exporter Registration and Reporting (ERN)
process?

If your app uses, accesses, implements or incorporates industry standard
encryption algorithms for purposes other than those listed as exemptions under
question 2, you need to submit for an ERN authorization. Examples of standard
encryption are: AES, SSL, https. This authorization requires that you submit an
annual report to two U.S. Government agencies with information about your app
every January. "

"2nd Question: Does your product qualify for any exemptions provided under
category 5 part 2?

There are several exemptions available in US export regulations under Category
5 Part 2 (Information Security & Encryption regulations) for applications and
software that use, access, implement or incorporate encryption.

All liabilities associated with misinterpretation of the export regulations or
claiming exemption inaccurately are borne by owners and developers of the apps.

You can answer “YES” to the question if you meet any of the following criteria:

(i) if you determine that your app is not classified under Category 5, Part 2
of the EAR based on the guidance provided by BIS at encryption question. The
Statement of Understanding for medical equipment in Supplement No. 3 to Part
774 of the EAR can be accessed at Electronic Code of Federal Regulations site.
Please visit the Question #15 in the FAQ section of the encryption page for
sample items BIS has listed that can claim Note 4 exemptions.

(ii) your app uses, accesses, implements or incorporates encryption for
authentication only

(iii) your app uses, accesses, implements or incorporates encryption with key
lengths not exceeding 56 bits symmetric, 512 bits asymmetric and/or 112 bit
elliptic curve

(iv) your app is a mass market product with key lengths not exceeding 64 bits
symmetric, or if no symmetric algorithms, not exceeding 768 bits asymmetric
and/or 128 bits elliptic curve.

Please review Note 3 in Category 5 Part 2 to understand the criteria for mass
market definition.

(v) your app is specially designed and limited for banking use or ‘money
transactions.’ The term ‘money transactions’ includes the collection and
settlement of fares or credit functions.

(vi) the source code of your app is “publicly available”, your app distributed
at free of cost to general public, and you have met the notification
requirements provided under 740.13.(e).

Please visit encryption web page in case you need further help in determining
if your app qualifies for any exemptions.

If you believe that your app qualifies for an exemption, please answer “YES” to
the question."

---------------------------------------------------------------------

_(Full legal text is [here](http://www.ecfr.gov/cgi-bin/text-idx?c=ecfr&rgn=div5&view=text&node=15:2.1.3.4.25&idno=15#15:2.1.3.4.25.0.1.13))_

EAR, paragraph 740.13.(e) [Free of cost, publicly available source code]

(e) Publicly available encryption source code.

(1) Scope and eligibility.  Subject to the notification requirements of
paragraph (e)(3) of this section, this paragraph (e) authorizes exports and
reexports of publicly available encryption source code classified under ECCN
5D002 that is subject to the EAR (see § 734.3(b)(3) of the EAR). Such source
code is eligible for License Exception TSU under this paragraph (e) even if it
is subject to an express agreement for the payment of a licensing fee or
royalty for commercial production or sale of any product developed using the
source code.

(2) Restrictions. This paragraph (e) does not authorize:

(i) Export or reexport of any encryption software classified under ECCN 5D002
that does not meet the requirements of paragraph (e)(1), even if the software
incorporates or is specially designed to use other encryption software that
meets the requirements of paragraph (e)(1) of this section; or

(ii) Any knowing export or reexport to a country listed in Country Group E:1 in
Supplement No. 1 to part 740 of the EAR.

(3) Notification requirement. You must notify BIS and the ENC Encryption
Request Coordinator via e-mail of the Internet location ( e.g., URL or Internet
address) of the publicly available encryption source code or provide each of
them a copy of the publicly available encryption source code. If you update or
modify the source code, you must also provide additional copies to each of them
each time the cryptographic functionality of the source code is updated or
modified. In addition, if you posted the source code on the Internet, you must
notify BIS and the ENC Encryption Request Coordinator each time the Internet
location is changed, but you are not required to notify them of updates or
modifications made to the encryption source code at the previously notified
location. In all instances, submit the notification or copy to
crypt@bis.doc.gov and to enc@nsa.gov.

Note to paragraph ( e ): Posting encryption source code on the Internet ( e.g.,
FTP or World Wide Web site) where it may be downloaded by anyone neither
establishes “knowledge” of a prohibited export or reexport for purposes of this
paragraph, nor triggers any “red flags” imposing a duty to inquire under the
“Know Your Customer” guidance provided in Supplement No. 3 to part 732 of the
EAR. Publicly available encryption object code software classified under ECCN
5D002 is not subject to the EAR when the corresponding source code meets the
criteria specified in this paragraph (e), see § 734.3(b)(3) of the EAR.
