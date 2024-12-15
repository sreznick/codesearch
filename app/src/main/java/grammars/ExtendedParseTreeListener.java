package grammars;

import codegrep.data.ProjectContents;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.nio.file.Path;

public interface ExtendedParseTreeListener extends ParseTreeListener {
    void setPath(Path f);
    ProjectContents getProjectContents();
}
