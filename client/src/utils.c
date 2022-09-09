#include "utils.h"

/**
 * 
 * Gets a substring from the desired bounds
 * 
 * Example: java_impl_substring_n("Hello, world!", 3, 5) = lo,
 * 
 **/
char* java_impl_substring_n(const char* src, size_t begin, size_t end) {
	assert(begin >= 0 && end <= strlen(src));	
	char* dest = malloc(sizeof(char) * (end - begin + 1));
	strncpy(dest, src + begin, end - begin);
	dest[end - begin] = '\0';
	return dest;
}

/**
 * 
 * Gets a substring starting from begin
 *  
 **/
char* java_impl_substring(const char* src, size_t begin) {
	return java_impl_substring_n(src, begin, strlen(src));
}

/**
 * 
 * Returns the char at the index
 * 
 **/
char* java_impl_char_at(const char* src, size_t index) {
	assert(index >= 0 && index < strlen(src));
	char* c = malloc(sizeof(char) * 2);
	strncpy(c, src + index, 1);
	c[1] = '\0';
	return c;
}

/**
 * 
 * Equivalent of String.split() in Java
 * 
 * You get a struct with the length & the values in an array of strings
 * 
 * DON'T FORGET TO FREE IT WITH java_type_splits_free()
 * 
 **/
java_type_splits* java_impl_split(const char* src, const char* sep) {
	char* src_dup = strdup(src);
	int length = 0;
	for (int i = 0; i < strlen(src_dup); i++) {
		if (src_dup[i] == *sep) {
			length++;
		}
	}
	java_type_splits* splits = malloc(sizeof(java_type_splits));
	if (length == 0) {
		splits->values = malloc(sizeof(char*) * 1);
		splits->values[0] = malloc(sizeof(char) * (strlen(src_dup) + 1));
		strncpy(splits->values[0], src_dup, strlen(src_dup));
		splits->values[0][strlen(src_dup)] = '\0';
		splits->length = 1;
	} else {
		splits->values = malloc(sizeof(char*) * (length + 1));
		char* token = strtok(src_dup, sep);
		int i = 0;
		while (token != NULL) {
			splits->values[i] = malloc(sizeof(char) * (strlen(token) + 1));
			strncpy(splits->values[i], token, strlen(token));
			splits->values[i][strlen(token)] = '\0';
			token = strtok(NULL, sep);
			i++;
		}
		splits->length = i;
	}
	free(src_dup);
	return splits;
}

/**
 * 
 * Compares 2 strings
 * 
 **/
BOOLEAN java_impl_equals(const char* s1, const char* s2) {
	if (strlen(s1) != strlen(s2)) return FALSE;
	return !(strcmp(s1, s2));
}

/**
 * 
 * Finds the index of the first occurrence of s2 in s1 starting from begin
 * 
 * Returns the position relative to the whole s1 and not just the subregion
 * 
 **/
ssize_t java_impl_index_of_n(const char* s1, const char* s2, size_t begin) {
	char* sub = java_impl_substring(s1, begin);
	char* found = strstr(sub, s2);
	if (found == NULL) {
		return -1;
	} else {
		int index = found - sub - begin;
		return index;
	}
}

/**
 * 
 * Same as java_impl_index_of_n but starts at 0
 * 
 **/
ssize_t java_impl_index_of(const char* s1, const char* s2) {
	return java_impl_index_of_n(s1, s2, 0);
}

/**
 * 
 * Just don't forget to use it after you're done using splits
 * 
 **/
void java_type_splits_free(java_type_splits* splits) {
	for (int i = 0; i < splits->length; i++) {
		free(splits->values[i]);
	}
	free(splits->values);
	free(splits);
}
