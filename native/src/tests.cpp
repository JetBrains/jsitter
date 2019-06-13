#include <tree_sitter/api.h>
#include <cstring>
#include <sys/time.h>
#include "java_api.cpp"
#include <cassert>

extern "C" {
TSLanguage *tree_sitter_json();
TSLanguage *tree_sitter_go();
}

bool ts_zipper_next(TSZipper *zipper, TSZipper *res, TSLanguage *lang) {
    bool down = ts_zipper_down(zipper, res, lang);
    if (down) {
        return true;
    }
    bool right = ts_zipper_right(zipper, res);
    if (right) {
        return true;
    }
    while ((zipper = ts_zipper_up(zipper))) {
        if (ts_zipper_right(zipper, res)) {
            return true;
        }
    }
    return false;
}

void perf() {
    FILE *f = fopen("/Users/jetzajac/Projects/jsitter/testData/router_go", "r");
    fseek (f, 0, SEEK_END);
    size_t s = ftell(f);
    rewind(f);
    void *b = malloc(s);
    fread(b, s, 1, f);
    TSParser *parser = ts_parser_new();
    ts_parser_set_language(parser, tree_sitter_go());
    struct timespec start, end;
    clock_gettime(CLOCK_MONOTONIC_RAW, &start);
    TSTree *tree = ts_parser_parse_string(parser,
                                          NULL,
                                          (const char *)b,
                                          s);
    clock_gettime(CLOCK_MONOTONIC_RAW, &end);
    uint64_t delta_us = (end.tv_sec - start.tv_sec) * 1000000 + (end.tv_nsec - start.tv_nsec) / 1000;
    printf("took %llu\n", delta_us);
}


int main () {
    TSParser *parser = ts_parser_new();
    TSLanguage *lang = tree_sitter_go();
    ts_parser_set_language(parser, lang);
//    const char *str = "{\"abc\": [12, \"hello\", {\"cde\": 1}], 1: 2}";
    const char *str_go = "func hello() { sayHello() }";
    TSTree *tree = ts_parser_parse_string(
                                          parser,
                                          NULL,
                                          str_go,
                                          strlen(str_go)
                                          );
    TSNode root_node = ts_tree_root_node(tree);
    char *string = ts_node_string(root_node);
    printf("Syntax tree: %s\n", string);
    TSZipper zipper;
    ts_zipper_new(tree, &zipper);
    TSZipper *prev = (TSZipper *)malloc(sizeof(TSZipper));
    *prev = zipper;
    while (true)  {
        TSZipper *r = (TSZipper *)malloc(sizeof(TSZipper));
        bool res = ts_zipper_next(prev, r, lang);
        if (!res) {
            break;
        }
        prev = r;
        TSSymbol s = ts_zipper_node_type(r);
        printf("%s\n", ts_language_symbol_name(lang, s));
    }
    perf();
    return 0;
}
