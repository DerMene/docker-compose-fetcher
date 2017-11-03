import spock.lang.Specification

class FetcherSpec extends Specification {

    def "Run fetcher and save results"() {
        given:
        def fetcher = new Fetcher()

        when:
        fetcher.fetch(System.getProperty("user.home") + "/ansible-role/vars/main.yml")

        then:
        noExceptionThrown()
    }
}
