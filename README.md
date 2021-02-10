# Lochkarte
A project template plugin for JetBrains Meta Programming System (MPS)

## Why?

Why do I need a special plugin for a project templates, can't I just copy my project on the file system or use Github template repository? Not really. Yes you can create a template and then just copy the files around but it has some serious side effects. The problem with this approach is that MPS models and modules (language/solution) have ids associated with them and MPS uses these ids to identify the model or module. If MPS sees two models or modules with same id it thinks they are the same. This means if you copy your template twice you can't open both of the copies in MPS at the same time because on the one you open first will be visible. When you open the second copy MPS will not load any of the content because it thinks it has already loaded them. This might not look like a big problem in the first place but can be a real pain in the long run. MPS uses these ids all over the place e.g. also to store references between nodes and duplicate ids can have really bad side effects and you want to avoid this at all cost. 

This plugin allows you to use a normal MPS project as a template. The plugin takes care of creating a copy of the template and making sure all models and modules get unique ids. 

## Templates

Templates are just normal MPS projects. You don't need to do anything special to make a project a template. The plugin will copy the complete content of the template including the files that aren't part of the MPS project itself e.g. build scripts and readme. During copying the project some file are ignore: 

- everything under `.git` because we don't want to copy the git repository 
- `workspace.xml` contains the state of the project e.g. open editors etc. we can't copy them.
- `modules.xml` contains the content of the project, we will create a new one as part of the project creation.

### Hosted Templates

### Macros

The plugin has very basic support for using macros within the template. Macros are replaced with their value after the
file containing them has been copied into the project.

Supported macros are: 

- `%%project-name%%`: replaced with the name of the project chosen by the users.

Macros are only replaced in text files. Currently supported file types are:

- gradle scripts: `*.gradle`, `*.gradle.kts`
- Java: `*.java`
- C: `*.c`, `*.h`
- C++: `.C`,`.cc`, `.cpp`,`.cxx`,`.c++`,`.h`,`.H`,`.hh`,`.hpp`,`.hxx`,`.h++`
- C#: `.cs`, `.csx`
- F#: `fs`, `.fsx`
- html: `*.html`, `*.htm`
- Markdown: `*.md`, `*.markdown`
- XML: `.xml` 

While the Lochkarte tries to detect the encoding of the files before opening and replacing the macro, it works best with
Unicode (UTF-8/UTF-16) files.

### Limitations

When using the "New Project" or "New Language" dialog with the "create sandbox/runtime" option MPS will create these
solutions below the language folder. Projects with this layout aren't supported because it causes lots of problems when
updating the ids. What this plugin does behind the scenes is, create a copy of the original module, delete the original
and then rename the copy to the original name. This is required because changing the descriptor, which contain the id,
after a module has been loaded doesn't work. During the renaming and deletion handling solution that are nested into a
language folder doesn't work right now.