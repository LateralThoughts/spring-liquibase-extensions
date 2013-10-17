package com.github.lateralthoughts.liquibase;

import static java.lang.String.format;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.github.lateralthoughts.liquibase.filter.CustomLiquibase;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.filter.ShouldRunChangeSetFilter;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;

/**
 * Substitute of {@link SpringLiquibase} with checks
 * of {@link Liquibase} {@link ChangeSet}s to execute.
 *
 * @author Florent Biville (@fbiville)
 */
public class SpringLiquibaseChecker extends SpringLiquibase {

    protected Liquibase createLiquibase(Connection c) throws LiquibaseException {
        Liquibase liquibase = new CustomLiquibase(getChangeLog(), createResourceOpener(), createDatabase(c));
        liquibase.setIgnoreClasspathPrefix(isIgnoreClasspathPrefix());
        return liquibase;
    }

    /**
     * <p>
     * Checks whether {@link ChangeSet}s need to be run when
     * Spring context initializes. If so, triggers an instance
     * of {@link LiquibaseException} to interrupt Spring
     * context startup.
     * </p>
     * <p>
     * This can be useful when changeset executions are not
     * driven by Spring (examples: Maven, Ant...).
     * <br />
     * In this scenario, this bean will prevent the application
     * to start if any unplanned migrations are detected.
     * <br />
     * <strong>NOTE:</strong> if only {@link ChangeSet}s marked
     * as <code>alwaysRun="true"</code> are detected, the
     * verification will trigger any exception.
     * </p>
     */
    @Override
    protected void performUpdate(Liquibase liquibase) throws LiquibaseException {
        Collection<ChangeSet> changeSets = filter(liquibase.listUnrunChangeSets(getContexts()));

        int size = changeSets.size();
        if (size > 0) {
            throw new UnexpectedLiquibaseChangesetException(
                    "%s changeset(s) has/have to run.\n" +
                            changeSetNames(changeSets) +
                            "This does *NOT* include changesets marked as 'alwaysRun'...\n" +
                            "\t...(unless they are marked as 'runOnChange' and have been altered).",
                    size
            );
        }
    }

    private Collection<ChangeSet> filter(Collection<ChangeSet> changeSets) {
        if (changeSets.isEmpty()) {
            return changeSets;
        }

        Collection<ChangeSet> result = new ArrayList<ChangeSet>();
        for (ChangeSet changeSet : changeSets) {
            if (hasBeenAltered(changeSet)) {
                result.add(changeSet);
            }
        }
        return result;
    }

    private boolean hasBeenAltered(ChangeSet changeSet) {
        return !changeSet.isAlwaysRun() || changeSet.isRunOnChange();
    }

    private String changeSetNames(Collection<ChangeSet> changeSets) {
        StringBuilder builder = new StringBuilder();
        for (ChangeSet changeSet : changeSets) {
            builder.append(format("\t%s (%s)\n", changeSet.getId(), stripClasspathPrefix(changeSet)));
        }
        return builder.toString();
    }

    private String stripClasspathPrefix(ChangeSet changeSet) {
        String filePath = changeSet.getFilePath();
        if (isIgnoreClasspathPrefix()) {
            return filePath.replace("classpath:", "");
        }
        return filePath;
    }
}
