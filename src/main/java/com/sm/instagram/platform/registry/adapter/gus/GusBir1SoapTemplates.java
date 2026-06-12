package com.sm.instagram.platform.registry.adapter.gus;

/**
 * SOAP XML envelope templates for GUS BIR 1.1 API.
 * Each template uses String.format() placeholders (%s) for dynamic values.
 * <p>
 * SOAP Actions reference:
 * <ul>
 *   <li>Zaloguj — authenticate with API key, returns session ID</li>
 *   <li>DaneSzukajPodmioty — search by NIP, returns basic entity data</li>
 *   <li>DanePobierzPelnyRaport — full report by REGON, returns all fields</li>
 *   <li>Wyloguj — release session</li>
 * </ul>
 */
public final class GusBir1SoapTemplates {

    private GusBir1SoapTemplates() {
    }

    static final String BIR11_NAMESPACE = "http://CIS/BIR/PUBL/2014/07";
    static final String DATA_CONTRACT_NAMESPACE = "http://CIS/BIR/PUBL/2014/07/DataContract";

    // SOAP Actions
    static final String ACTION_ZALOGUJ = "http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/Zaloguj";
    static final String ACTION_WYLOGUJ = "http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/Wyloguj";
    static final String ACTION_DANE_SZUKAJ = "http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/DaneSzukajPodmioty";
    static final String ACTION_DANE_PELNY_RAPORT = "http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/DanePobierzPelnyRaport";

    // Report types
    static final String REPORT_OSOBA_PRAWNA = "BIR11OsPrawna";
    static final String REPORT_OSOBA_FIZYCZNA_CEIDG = "BIR11OsFizycznaDzialalnoscCeidg";
    static final String REPORT_OSOBA_FIZYCZNA_DANE_OGOLNE = "BIR11OsFizycznaDaneOgolne";
    static final String REPORT_OSOBA_PRAWNA_PKD = "BIR11OsPrawnaPkd";
    static final String REPORT_OSOBA_FIZYCZNA_PKD = "BIR11OsFizycznaPkd";

    /**
     * Login envelope — authenticates with the API key and returns a session ID.
     * %s = API key
     */
    static final String ZALOGUJ = """
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ns="http://CIS/BIR/PUBL/2014/07">
              <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
                <wsa:To>https://wyszukiwarkaregon.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc</wsa:To>
                <wsa:Action>http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/Zaloguj</wsa:Action>
              </soap:Header>
              <soap:Body>
                <ns:Zaloguj>
                  <ns:pKluczUzytkownika>%s</ns:pKluczUzytkownika>
                </ns:Zaloguj>
              </soap:Body>
            </soap:Envelope>""";

    /**
     * Search by NIP — returns basic entity data including REGON and entity type.
     * %s = NIP to search for (session ID is passed via HTTP "sid" header)
     */
    static final String DANE_SZUKAJ_PODMIOTY = """
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ns="http://CIS/BIR/PUBL/2014/07" xmlns:dat="http://CIS/BIR/PUBL/2014/07/DataContract">
              <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
                <wsa:To>https://wyszukiwarkaregon.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc</wsa:To>
                <wsa:Action>http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/DaneSzukajPodmioty</wsa:Action>
              </soap:Header>
              <soap:Body>
                <ns:DaneSzukajPodmioty>
                  <ns:pParametryWyszukiwania>
                    <dat:Nip>%s</dat:Nip>
                  </ns:pParametryWyszukiwania>
                </ns:DaneSzukajPodmioty>
              </soap:Body>
            </soap:Envelope>""";

    /**
     * Full report — returns all fields for a given REGON and report type.
     * %1$s = REGON (9 or 14 digits)
     * %2$s = report type (e.g., BIR11OsPrawna, BIR11OsFizycznaDaneOgolne)
     */
    static final String DANE_POBIERZ_PELNY_RAPORT = """
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ns="http://CIS/BIR/PUBL/2014/07">
              <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
                <wsa:To>https://wyszukiwarkaregon.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc</wsa:To>
                <wsa:Action>http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/DanePobierzPelnyRaport</wsa:Action>
              </soap:Header>
              <soap:Body>
                <ns:DanePobierzPelnyRaport>
                  <ns:pRegon>%s</ns:pRegon>
                  <ns:pNazwaRaportu>%s</ns:pNazwaRaportu>
                </ns:DanePobierzPelnyRaport>
              </soap:Body>
            </soap:Envelope>""";

    /**
     * Logout — releases the session.
     * %s = session ID
     */
    static final String WYLOGUJ = """
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ns="http://CIS/BIR/PUBL/2014/07">
              <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
                <wsa:To>https://wyszukiwarkaregon.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc</wsa:To>
                <wsa:Action>http://CIS/BIR/PUBL/2014/07/IUslugaBIRzewnPubl/Wyloguj</wsa:Action>
              </soap:Header>
              <soap:Body>
                <ns:Wyloguj>
                  <ns:pIdentyfikatorSesji>%s</ns:pIdentyfikatorSesji>
                </ns:Wyloguj>
              </soap:Body>
            </soap:Envelope>""";
}
