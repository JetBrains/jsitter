#include <tree_sitter/api.h>
#include <string.h>
#include <sys/time.h>
//#include "java_api.cpp"
#include <assert.h>

TSLanguage *tree_sitter_json();
TSLanguage *tree_sitter_go();

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

void ranges() {
    TSParser *parser = ts_parser_new();
    TSLanguage *lang = tree_sitter_go();
    ts_parser_set_language(parser, lang);
    const char *str_go = "func hello() { sayHello() }";
    TSTree *tree = ts_parser_parse_string(
                                          parser,
                                          NULL,
                                          str_go,
                                          strlen(str_go)
                                          );
    const char *str_go2 = "func hello() { sayHello }";
    TSInputEdit e; // + 8
    e.start_byte = 15 + 8;
    e.old_end_byte = 15 + 8 + 2;
    e.new_end_byte = 15 + 8;
    ts_tree_edit(tree, &e);
    TSTree *tree2 = ts_parser_parse_string(parser, tree, str_go2, strlen(str_go2));
    uint32_t len;
    
    
    TSRange *ranges = ts_tree_get_changed_ranges(tree, tree2, &len);
    
    char *new_string = ts_node_string(ts_tree_root_node(tree2));
    printf("New Syntax tree: %s\n", new_string);
    char *old_string = ts_node_string(ts_tree_root_node(tree));
    printf("Old Syntax tree: %s\n", old_string);
    
    const char *str_go3 = "func hello() { sayHello() }";
    TSInputEdit e2; // + 8
    e2.start_byte = 15 + 8;
    e2.new_end_byte = 15 + 8 + 2;
    e2.old_end_byte = 15 + 8;
    ts_tree_edit(tree2, &e2);
    TSTree *tree3 = ts_parser_parse_string(parser, tree2, str_go3, strlen(str_go3));
    uint32_t len2;
    
    
    TSRange *ranges2 = ts_tree_get_changed_ranges(tree, tree2, &len2);
    
    char *new_string3 = ts_node_string(ts_tree_root_node(tree3));
    printf("New Syntax tree: %s\n", new_string3);
}

void str_insert(size_t index, char c, char *str, size_t size) {
  memmove(str + index + 1, str + index, size - index);
  str[index] = c;
}

void test_crash() {
  FILE *f = fopen("/Users/jetzajac/Projects/jsitter/testData/router_go", "r");
  fseek (f, 0, SEEK_END);
  size_t s = ftell(f);
  rewind(f);
  void *b = malloc(s + 1000);
  fread(b, s, 1, f);
  TSParser *parser = ts_parser_new();
  ts_parser_set_language(parser, tree_sitter_go());
  TSTree *tree = ts_parser_parse_string(parser,
                                        NULL,
                                        (const char *)b,
                                        s);
  TSTree *copy = ts_tree_copy(tree);
  //for (int i = 0; i < 100; ++i) {
  int i = 2; {
    printf("i = %d\n", i);
    //str_insert(1000 + i, 'x', b, s + i);
    TSTree *copy = ts_tree_copy(tree);
    TSInputEdit e; // + 8
    e.start_byte = 2000;
    e.old_end_byte = 2000;
    e.new_end_byte = 2000 + i;
    ts_tree_edit(copy, &e);
    TSTree *new_tree = ts_parser_parse_string(parser, copy, b, s);
    ts_tree_delete(copy);
    ts_tree_delete(new_tree);
    ts_parser_reset(parser);
  }
}


int main () {
  //    ranges();
    test_crash();
    //perf();
    return 0;
}
