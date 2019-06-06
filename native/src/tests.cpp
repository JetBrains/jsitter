#include <tree_sitter/api.h>
#include <cstring>

extern "C" {
TSLanguage *tree_sitter_json();
TSLanguage *tree_sitter_go();
}

int main () {
    TSParser *parser = ts_parser_new();
    ts_parser_set_language(parser, tree_sitter_go());
    const char *str = "{\"abc\": [12, \"hello\", {\"cde\": 1}], 1: 2}";
    const char *str_go = "func f() {}";
    TSTree *tree = ts_parser_parse_string(
                                          parser,
                                          NULL,
                                          str_go,
                                          strlen(str_go)
                                          );
    TSNode root_node = ts_tree_root_node(tree);
    char *string = ts_node_string(root_node);
    printf("Syntax tree: %s\n", string);

    return 0;
}
