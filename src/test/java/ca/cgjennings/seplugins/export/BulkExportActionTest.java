package ca.cgjennings.seplugins.export;

import static org.assertj.core.api.Assertions.assertThat;

import ca.cgjennings.apps.arkham.project.Member;
import ca.cgjennings.apps.arkham.project.Project;
import ca.cgjennings.apps.arkham.project.Task;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BulkExportActionTest {

    @TempDir
    Path root;

    private File projectFolder;
    private Project project;

    @BeforeEach
    void createProject() throws IOException {
        projectFolder = Project.createProject(root.toRealPath().toFile(), "proj");
        Task.createTask(projectFolder, "cards", "DECK");
    }

    @AfterEach
    void closeProject() {
        if (project != null) {
            project.close();
        }
    }

    private void touch(String path) throws IOException {
        Path p = projectFolder.toPath().resolve(path);
        Files.createDirectories(p.getParent());
        Files.createFile(p);
    }

    private Member open(String path) throws IOException {
        project = new Project(projectFolder);
        return path.isEmpty() ? project : project.findMember(new File(projectFolder, path));
    }

    private static List<String> collect(Member m) {
        List<Member> found = new ArrayList<>();
        BulkExportAction.buildList(new Member[]{m}, found);
        List<String> names = new ArrayList<>();
        for (Member f : found) {
            names.add(f.getName());
        }
        return names;
    }

    @Test
    void collectsFromProject() throws IOException {
        touch("top.eon");
        touch("cards/a.eon");
        touch("cards/notes.txt");
        touch("cards/sub/b.eon");
        assertThat(collect(open(""))).containsExactlyInAnyOrder("top.eon", "a.eon", "b.eon");
    }

    @Test
    void collectsFromTask() throws IOException {
        touch("top.eon");
        touch("cards/a.eon");
        touch("cards/sub/b.eon");
        assertThat(collect(open("cards"))).containsExactlyInAnyOrder("a.eon", "b.eon");
    }

    @Test
    void collectsFromPlainFolder() throws IOException {
        touch("cards/a.eon");
        touch("cards/sub/b.eon");
        touch("cards/sub/deeper/c.eon");
        assertThat(collect(open("cards/sub"))).containsExactlyInAnyOrder("b.eon", "c.eon");
    }

    @Test
    void collectsSingleComponent() throws IOException {
        touch("cards/a.eon");
        assertThat(collect(open("cards/a.eon"))).containsExactly("a.eon");
    }

    @Test
    void appliesToProjectWithNestedComponent() throws IOException {
        touch("cards/sub/b.eon");
        assertThat(BulkExportAction.buildList(new Member[]{open("")}, null)).isTrue();
    }

    @Test
    void doesNotApplyWithoutComponents() throws IOException {
        touch("cards/notes.txt");
        Files.createDirectories(projectFolder.toPath().resolve("cards/empty"));
        Member p = open("");
        assertThat(BulkExportAction.buildList(new Member[]{p}, null)).isFalse();
        assertThat(collect(p)).isEmpty();
    }
}
