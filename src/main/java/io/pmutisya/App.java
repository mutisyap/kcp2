package io.pmutisya;

import io.pmutisya.config.ApplicationConfiguration;
import io.pmutisya.config.ConfigurationUtil;
import io.pmutisya.config.HazelcastConfiguration;
import io.pmutisya.factory.KafkaCDRFileFactory;
import io.pmutisya.mulika.MulikaInstanceConfiguration;
import io.pmutisya.repository.CDRFileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Hello world!
 */
public class
App {
    private static final Logger logger = LogManager.getLogger(App.class);
    private static final CDRFileRepository cdrFileRepository = new CDRFileRepository();
    private static KafkaCDRFileFactory kafkaCDRFileFactory;
    private static Thread mainThread;

    public static void main(String[] args) throws IOException, InterruptedException {
        ApplicationConfiguration applicationConfiguration = ConfigurationUtil.readConfigurations();
        logger.info("Received application configurations : {}", applicationConfiguration);

        kafkaCDRFileFactory = new KafkaCDRFileFactory(applicationConfiguration, cdrFileRepository);
        logger.info("Instantiated kafkaCDRFileFactory : {}", kafkaCDRFileFactory);

        kafkaCDRFileFactory.initializeKafkaProducer();
        logger.info("Successfully initialized kafka producer");

        kafkaCDRFileFactory.initializeReaderThreads();
        logger.info("Successfully initialized reader threads");

        kafkaCDRFileFactory.startThreads();
        logger.info("Successfully started cdr reader threads");

        setUpMulikaClient(applicationConfiguration);
        logger.info("Successfully set up Mulika Client");

        logger.info("Successfully started Application");

        //add shutdown hook
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        mainThread = Thread.currentThread();

        checkApplicationHealth();
    }

    private static void setUpMulikaClient(ApplicationConfiguration applicationConfiguration) {
        MulikaInstanceConfiguration.setUpMulikaClientInstance(
                applicationConfiguration.getMulikaURL(),
                applicationConfiguration.getMulikaApiKey(),
                applicationConfiguration.getReportStatsIntervalMs(),
                applicationConfiguration.getApplicationName(),
                applicationConfiguration.getModuleName());
    }

    private static void checkApplicationHealth() {
        while (true) {
            try {
                // do nothing
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                shutDownApplication();
                break;
            } catch (Exception ex) {
                logger.warn("Encountered exception", ex);
            }
        }
    }

    public static void shutDownApplication() {
        logger.info("Shutting down Application");
        try {
            kafkaCDRFileFactory.shutDown();
            logger.info("Shutdown successful. Bye");
            Thread.currentThread().interrupt();
            mainThread.interrupt();
            HazelcastConfiguration.getHazelcastInstance().shutdown();
        } catch (Exception ex) {
            logger.error("Error shutting down", ex);
        }
    }

    static class ShutdownHook extends Thread {
        @Override
        public void run() {
            shutDownApplication();
        }
    }
}
