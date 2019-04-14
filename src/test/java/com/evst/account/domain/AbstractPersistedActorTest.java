package com.evst.account.domain;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.evst.account.TemporaryFolderExtension;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public abstract class AbstractPersistedActorTest {

    @Getter protected Config config;
    @Getter protected ActorSystem system;

    @Getter protected TemporaryFolderExtension temporaryFolder = new TemporaryFolderExtension();
    @Getter protected File journal;
    @Getter protected File snapshot;

    @BeforeEach
    public void setup() throws IOException {
        temporaryFolder.create();
        temporaryFolder.getRoot().setWritable(true);
        journal = temporaryFolder.newFolder("temp_journal");
        snapshot = temporaryFolder.newFolder("temp_snapshot");
        config = ConfigFactory.load("application").withValue(
            "akka.persistence.journal.leveldb.dir", fromAnyRef(journal.getAbsolutePath())
        ).withValue(
            "akka.persistence.snapshot-store.local.dir", fromAnyRef(snapshot.getAbsolutePath())
        );
        system = ActorSystem.create(
            "test",
            config
        );
    }

    @AfterEach
    public void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
        temporaryFolder.cleanUp();
    }

}
