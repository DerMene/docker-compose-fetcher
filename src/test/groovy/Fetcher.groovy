import spock.lang.Specification

class FetcherSpec extends Specification {

    def "Run fetcher and save results"() {
        given:
        def fetcher = new Fetcher()

        when:
        fetcher.fetch()

        expect:
        noExceptionThrown()
    }
}
