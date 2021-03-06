package org.arquillian.smart.testing.scm.git;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import org.arquillian.smart.testing.scm.Change;
import org.arquillian.smart.testing.scm.ChangeType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

public class GitChangeResolverTest {

    @Rule
    public final TemporaryFolder gitFolder = new TemporaryFolder();

    private GitChangeResolver gitChangeResolver;

    @Before
    public void unpack_repo() {
        final URL repoBundle = Thread.currentThread().getContextClassLoader().getResource("repo.bundle");
        GitRepositoryUnpacker.unpackRepository(gitFolder.getRoot().getAbsolutePath(), repoBundle.getFile());
    }

    @After
    public void closeRepo() throws Exception {
        this.gitChangeResolver.close();
    }

    @Test
    public void should_fetch_only_gitignore_in_diff_between_two_immediate_commits() throws Exception {
        // given
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "32bd752", "07b181b");

        // when
        final Set<Change> diff = gitChangeResolver.diff();

        // then
        assertThat(diff).hasSize(1).extracting(Change::getLocation, Change::getChangeType).containsOnly(tuple(
            relative(".gitignore"), ChangeType.ADD));
    }

    @Test
    public void should_fetch_all_files_from_first_commit_to_given_hash() throws Exception {
        // given
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "d923b3a", "1ee4abf");

        // when
        final Set<Change> diff = gitChangeResolver.diff();

        // then
        assertThat(diff).hasSize(18);
    }

    @Test
    public void should_fetch_all_untracked_files() throws IOException {
        // given
        gitFolder.newFile("untracked.txt");
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "HEAD", "HEAD");

        // when
        final Set<Change> untrackedChanges = gitChangeResolver.diff();

        // then
        assertThat(untrackedChanges).hasSize(1).extracting(Change::getLocation, Change::getChangeType).containsOnly(tuple(
            relative("untracked.txt"), ChangeType.ADD));
    }

    @Test
    public void should_fetch_all_added_files() throws IOException, GitAPIException {
        // given
        gitFolder.newFile("newadd.txt");
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "HEAD", "HEAD");
        GitRepositoryOperations.addFile(gitFolder.getRoot(), "newadd.txt");

        // when
        final Set<Change> newStagedChanges = gitChangeResolver.diff();

        // then
        assertThat(newStagedChanges).hasSize(1).extracting(Change::getLocation, Change::getChangeType).containsOnly(tuple(
            relative("newadd.txt"), ChangeType.ADD));
    }

    @Test
    public void should_fetch_all_modified_files() throws IOException {
        // given
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "HEAD", "HEAD");
        final Path readme = Paths.get(gitFolder.getRoot().getAbsolutePath(), "README.adoc");
        Files.write(readme, "More".getBytes(), StandardOpenOption.APPEND);

        // when
        final Set<Change> modifiedChanges = gitChangeResolver.diff();

        // then
        assertThat(modifiedChanges).hasSize(1).extracting(Change::getLocation, Change::getChangeType).containsOnly(tuple(
            relative("README.adoc"), ChangeType.MODIFY));
    }

    @Test
    public void should_return_meaningful_exception_when_incorrect_previous_commit_provided() throws Exception {
        // given
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "a34a06478ef3957c866cff3f546f2c55c1a39364", "07b181b");

        // when
        final Throwable throwable = catchThrowable(() -> gitChangeResolver.diff());

        // then
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Commit id 'a34a06478ef3957c866cff3f546f2c55c1a39364' is not found in");
    }

    @Test
    public void should_return_meaningful_exception_when_incorrect_head_provided() throws Exception {
        // given
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "07b181b", "e195e3767591fbd041e041877c541229afaac3c9");

        // when
        final Throwable throwable = catchThrowable(() -> gitChangeResolver.diff());

        // then
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Commit id 'e195e3767591fbd041e041877c541229afaac3c9' is not found in");
    }

    @Test
    public void should_return_meaningful_exception_when_null_commit_provided() throws Exception {
        // given
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "null", "07b181b");

        // when
        final Throwable throwable = catchThrowable(() -> gitChangeResolver.diff());

        // then
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Commit id 'null' is not found in");
    }

    @Test
    public void should_return_meaningful_exception_when_empty_commit_provided() throws Exception {
        // given
        this.gitChangeResolver = new GitChangeResolver(gitFolder.getRoot(), "", "07b181b");

        // when
        final Throwable throwable = catchThrowable(() -> gitChangeResolver.diff());

        // then
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Commit id '' is not found in");
    }

    private Path relative(String path) {
        return Paths.get(gitFolder.getRoot().getAbsolutePath(), path);
    }
}
