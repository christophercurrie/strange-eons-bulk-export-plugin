package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.StrangeEons;
import ca.cgjennings.apps.arkham.project.Member;
import ca.cgjennings.apps.arkham.project.Project;
import ca.cgjennings.apps.arkham.project.ProjectUtilities;
import ca.cgjennings.apps.arkham.project.ProjectView;
import ca.cgjennings.apps.arkham.project.Task;
import ca.cgjennings.apps.arkham.project.TaskAction;
import java.util.LinkedList;
import java.util.List;
import static resources.Language.string;

/**
 * Project action that allows the export of game components to images in bulk.
 *
 * @author Chris Jennings <https://cgjennings.ca/contact>
 * @since 3.0
 */
public class BulkExportAction extends TaskAction {

    @Override
    public String getLabel() {
        return string("bx-action-name");
    }

    @Override
    public boolean perform(Project project, Task task, Member member) {
        return performOnSelection(new Member[]{ProjectUtilities.simplify(project, task, member)});
    }

    @Override
    public boolean appliesTo(Project project, Task task, Member member) {
        return appliesToSelection(new Member[]{ProjectUtilities.simplify(project, task, member)});
    }

    @Override
    public boolean appliesToSelection(Member[] members) {
        return buildList(members, null);
    }

    @Override
    public boolean performOnSelection(Member[] members) {
        LinkedList<Member> exportList = new LinkedList<>();
        buildList(members, exportList);
        if (!exportList.isEmpty()) {
            BulkExportDialog bxd = new BulkExportDialog(exportList);
            ProjectView v = StrangeEons.getWindow().getOpenProjectView();
            if (v != null) {
                v.moveToLocusOfAttention(bxd);
            } else {
                bxd.setLocationRelativeTo(StrangeEons.getWindow());
            }
            bxd.setVisible(true);
        }
        return true;
    }

    private boolean buildList(Member[] members, List<Member> suitable) {
        int matches = 0;
        outer:
        for (Member m : members) {
            if (m.hasChildren()) {
                for (Member kid : m.getChildren()) {
                    if (ProjectUtilities.matchExtension(kid, "eon")) {
                        ++matches;
                        if (suitable == null) {
                            break outer;
                        } else {
                            suitable.add(kid);
                        }
                    }
                }
            } else if (ProjectUtilities.matchExtension(m, "eon")) {
                ++matches;
                if (suitable == null) {
                    break outer;
                } else {
                    suitable.add(m);
                }
            }
        }
        // if suitable is null, we only care IF the command applies, so
        // we break out of the loop as soon as possible
        return matches > 0;
    }
}
