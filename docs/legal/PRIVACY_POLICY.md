# Privacy Policy

**Last updated:** June 20, 2026

This Privacy Policy describes how James Carson ("**Operator**," "**we**," "**us**," or "**our**") collects, uses, and shares information when you use the Equity Analytics API and related websites or documentation (collectively, the "**Service**").

By using the Service, you acknowledge this Privacy Policy. If you do not agree, do not use the Service.

---

## 1. Scope

This policy applies to information we process in operating the Service. It does **not** apply to third-party websites, market data vendors, or services you access through your own integrations.

---

## 2. Information We Collect

### 2.1 Information you provide

We may collect information you voluntarily provide, such as:

- Email or contact details if you request support, licensing, or commercial access
- API key registration details if we offer key provisioning in the future
- Feedback, bug reports, or correspondence

We do **not** require account registration for basic API access today.

### 2.2 Information collected automatically

When you use the Service, we may automatically collect:

- **Request metadata:** IP address, timestamps, HTTP method, URL path, query parameters (excluding secrets), user agent, and referrer
- **API usage:** Rate-limit counters, API key identifiers (hashed or truncated where stored), endpoint usage, response status codes, and latency
- **Operational logs:** Error messages, correlation IDs, and diagnostic data for reliability and security
- **Infrastructure metrics:** CPU, memory, request volume, and similar telemetry via monitoring tools (e.g., Prometheus)

We do **not** intentionally collect passwords, payment card numbers, or government ID numbers through the current API. If paid billing is introduced, payment processing will be handled by a third-party processor subject to their privacy terms.

### 2.3 API request content

The Service processes **financial symbols and date ranges** you submit (e.g., ticker symbols, `from_date`, `to_date`). We treat this as **operational data**, not personal information, unless you submit personal data in parameters (which you should not do).

We do **not** store portfolio holdings, account balances, or personally identifiable financial account information unless you explicitly provide such data in a future feature designed for it.

### 2.4 Cookies and similar technologies

The API itself does not use cookies. If we publish a website or documentation portal with analytics or session features, we may use cookies or local storage as described in an updated notice at that time.

---

## 3. How We Use Information

We use collected information to:

- Provide, maintain, and improve the Service
- Enforce rate limits, API keys, and security controls
- Monitor performance, diagnose errors, and prevent abuse
- Respond to support or licensing inquiries
- Comply with legal obligations
- Plan and operate future paid or commercial offerings

We do **not** sell your personal information.

We do **not** use API request data to provide personalized investment advice.

---

## 4. Legal Bases (EEA/UK users)

Where applicable (e.g., GDPR), we process personal data based on:

- **Contract:** To provide the Service you request
- **Legitimate interests:** Security, abuse prevention, analytics, and Service improvement
- **Legal obligation:** Where required by law
- **Consent:** Where we ask for it explicitly (e.g., marketing emails, if offered)

---

## 5. How We Share Information

We may share information with:

- **Infrastructure providers:** Hosting, logging, monitoring, and CDN vendors that process data on our behalf under contractual confidentiality and security obligations
- **Market data providers:** Request parameters necessary to fetch data (e.g., symbol, date range) are sent to third parties such as Alpha Vantage under their terms
- **Legal and safety:** When required by law, subpoena, or to protect rights, safety, and integrity of the Service
- **Business transfers:** In connection with a merger, acquisition, or asset sale, subject to continued protection consistent with this policy

We do not share personal information with advertisers for targeted advertising.

---

## 6. Data Retention

We retain information only as long as reasonably necessary for the purposes above:

| Data type | Typical retention |
|-----------|-------------------|
| Access and application logs | 30–90 days (unless longer retention needed for security investigations) |
| Aggregated metrics | Longer, in anonymized or statistical form |
| Support correspondence | Up to 3 years after resolution |
| API key usage records | Duration of key validity plus a reasonable audit period |

We may retain information longer when required by law or to establish, exercise, or defend legal claims.

---

## 7. Security

We implement reasonable administrative, technical, and organizational measures designed to protect information, including TLS in production deployments, API key authentication options, rate limiting, and access controls on infrastructure.

**No method of transmission or storage is 100% secure.** Use the Service at your own risk and protect your API keys accordingly.

---

## 8. Your Rights and Choices

Depending on your location, you may have rights to:

- Access personal information we hold about you
- Request correction or deletion
- Object to or restrict certain processing
- Data portability
- Withdraw consent where processing is consent-based
- Lodge a complaint with a supervisory authority

To exercise these rights, contact us at **jamescarson3rd@gmail.com**. We may verify your identity before responding.

### California residents (CCPA/CPRA)

We do not sell personal information. California residents may request disclosure of categories collected and deletion, subject to legal exceptions.

---

## 9. International Transfers

If you access the Service from outside the United States, your information may be processed in the U.S. or other countries with different data protection laws. By using the Service, you consent to such transfers where permitted by law.

---

## 10. Children

The Service is not directed to individuals under **13** (or 16 where applicable under local law). We do not knowingly collect personal information from children. Contact us if you believe we have collected such information and we will delete it.

---

## 11. Third-Party Links and Data Vendors

Documentation may link to third-party sites (e.g., Alpha Vantage, GitHub). Their privacy practices govern data you provide to them. Review their policies separately.

Market data returned by the Service originates from third-party sources. We are not responsible for their data handling practices.

---

## 12. Changes to This Policy

We may update this Privacy Policy from time to time. The "Last updated" date will change when we do. Material changes may be communicated via the repository or Service documentation. Continued use after changes constitutes acknowledgment.

---

## 13. Contact

**James Carson**  
Email: jamescarson3rd@gmail.com

Related documents: [Terms of Service](TERMS_OF_SERVICE.md) · [NOTICE.md](../NOTICE.md) · [LICENSE](../../LICENSE)
