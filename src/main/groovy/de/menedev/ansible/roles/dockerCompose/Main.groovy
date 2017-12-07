package de.menedev.ansible.roles.dockerCompose

import groovy.util.logging.Slf4j
import java.nio.file.Paths

@Slf4j
class Main {
    static private final String REPOSITORY_URL = "https://api.github.com/repos/docker/compose/releases"

    public static void main(String ...args) {
        if (args.length < 1) {
            log.error "parameters: <output>"
            System.exit(1)
        }

        def outputPath = Paths.get(args[0])
        if (!outputPath.toFile().canRead()) {
            log.error "$outputPath not writable"
            System.exit(2)
        }

        final URL url = new URL(REPOSITORY_URL)
        def converter = new UrlToStreamConverter()
        def updater = new Updater(converter)
        def releases = updater.getReleases(url)
        updater.update(outputPath.newOutputStream(), releases)
    }
}
