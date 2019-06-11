#include <tree_sitter/api.h>
#include <cstring>
#include <sys/time.h>
#include "java_api.cpp"

extern "C" {
TSLanguage *tree_sitter_json();
TSLanguage *tree_sitter_go();
}

void perf() {
    FILE *f = fopen("/Users/jetzajac/Projects/jsitter/testData/router.go", "r");
    fseek (f, 0, SEEK_END);
    size_t s = ftell(f);
    rewind(f);
    void *b = malloc(s);
    fread(b, s, 1, f);
    TSParser *parser = ts_parser_new();
    ts_parser_set_language(parser, tree_sitter_go());
    TSTree *tree = ts_parser_parse_string(parser,
                                          NULL,
                                          (const char *)b,
                                          s);
    TSNode root_node = ts_tree_root_node(tree);
    TSZipper *z = new_zipper(root_node);
    struct timespec start, end;
    clock_gettime(CLOCK_MONOTONIC_RAW, &start);
    
    while (zipper_move<NEXT>(z, false, 0, false)) {
        
    }
    clock_gettime(CLOCK_MONOTONIC_RAW, &end);
    uint64_t delta_us = (end.tv_sec - start.tv_sec) * 1000000 + (end.tv_nsec - start.tv_nsec) / 1000;
    printf("took %llu\n", delta_us);
}

int main () {
    
    TSParser *parser = ts_parser_new();
    ts_parser_set_language(parser, tree_sitter_go());
//    const char *str = "{\"abc\": [12, \"hello\", {\"cde\": 1}], 1: 2}";
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
    TSZipper *z = new_zipper(root_node);
    while (zipper_move<NEXT>(z, false, 0, false)) {
        printf("*");
    }
    perf();
    return 0;
}
