#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <unistd.h>

#define BOOLEAN uint8_t
#define TRUE 1
#define FALSE 0

typedef struct {
	size_t length;
	char** values;
} java_type_splits;

char* java_impl_substring_n(const char*, size_t, size_t);
char* java_impl_substring(const char*, size_t);
char* java_impl_char_at(const char*, size_t);
java_type_splits* java_impl_split(const char*, const char*);
BOOLEAN java_impl_equals(const char*, const char*);
ssize_t java_impl_index_of_n(const char*, const char*, size_t);
ssize_t java_impl_index_of(const char*, const char*);
BOOLEAN java_impl_is_empty(char*);

void java_type_splits_free(java_type_splits*);

