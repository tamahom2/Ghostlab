#include <pthread.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include "utils.h"

#define DEBUG FALSE

#define DEFAULT_PORT 43244
#define DEFAULT_BUFFER_SIZE 1024

volatile BOOLEAN keep_going;
volatile BOOLEAN in_game;

size_t player_udp_port;

typedef struct {
	char* party_net_address;
	size_t party_udp_port;
} udp_party_info;

/**
 * 
 * Checks if command is terminated correctly, with ***
 * 
 **/
BOOLEAN is_command_valid(const char* command) {
	char* terminator = java_impl_substring(command, strlen(command) - 3);
	char* pre_terminator = java_impl_char_at(command, strlen(command) - 4);
	BOOLEAN valid = java_impl_equals(terminator, "***") && pre_terminator[0] != '*';
	free(terminator);
	free(pre_terminator);
	return valid;
}

/**
 * 
 * TODO: Complete & test this, to receive UDP messages from other players while in-game
 * 
 **/
void* udp_read_players(void* arg) {
	#define BUFFER 256
	int port = *((int*) arg);
	int udp_socket = socket(PF_INET, SOCK_DGRAM, 0);
	struct sockaddr_in socket_address;
	socket_address.sin_family = AF_INET;
	socket_address.sin_port = htons(port);
	socket_address.sin_addr.s_addr = htonl(INADDR_ANY);
	int ret_value = bind(udp_socket, (struct sockaddr *) &socket_address, sizeof(struct sockaddr_in));
	if (ret_value == 0) {
		if (DEBUG) {
			printf("[DEBUG:\n\tAWAITING PLAYERS' MESSAGES ON PORT %d\n]\n", port);
		}
		char buffer[BUFFER];
		while (keep_going && in_game) {
			int received = recv(udp_socket, buffer, BUFFER, 0);
			buffer[received] = '\0';
			printf("%s\n", buffer);
		}
		close(udp_socket);
	}
	return NULL;
}

/**
 * 
 * TODO: Fill this to listen from UDP party
 * 
 **/
void* udp_read_party(void* arg) {
	#define BUFFER 256
	udp_party_info party_info = *((udp_party_info *) arg);
	int udp_socket = socket(PF_INET, SOCK_DGRAM, 0);
	int flag = 1;
	int ret_value = setsockopt(udp_socket, SOL_SOCKET, SO_REUSEPORT, &flag, sizeof(flag));
	struct sockaddr_in address_sock;
	address_sock.sin_family = AF_INET;
	address_sock.sin_port = htons(party_info.party_udp_port);
	address_sock.sin_addr.s_addr = htonl(INADDR_ANY);
	ret_value = bind(udp_socket, (struct sockaddr *) &address_sock, sizeof(struct sockaddr_in));
	struct ip_mreq multicast_req;
	multicast_req.imr_multiaddr.s_addr = inet_addr(party_info.party_net_address);
	multicast_req.imr_interface.s_addr = htonl(INADDR_ANY);
	ret_value = setsockopt(udp_socket, IPPROTO_IP, IP_ADD_MEMBERSHIP, &multicast_req, sizeof(multicast_req));
	if (ret_value == 0) {
		if (DEBUG) {
			printf("[DEBUG:\n\tAWAITING PARTY MESSAGES ON %s:%ld\n]\n", party_info.party_net_address, party_info.party_udp_port);
		}
		char buffer[BUFFER];
		while (keep_going && in_game) {
			int received = recv(udp_socket, buffer, BUFFER, 0);
			buffer[received] = '\0';
			printf("%s\n", buffer);
		}
		close(udp_socket);
	} else {
		perror("Could not subscribe to multicast address");
	}
	return NULL;
}

/**
 * 
 * TODO: Complete & test this to received party multicasted messages
 * 
 **/

char* read_tcp_request(int socket_fd) {
	char* command_buffer = malloc(sizeof(char) * DEFAULT_BUFFER_SIZE);
	char* single_byte_buffer = malloc(sizeof(char) * 1);
	size_t index_written = 0;
	size_t index_read = 0;
	char* command = NULL;
	while (TRUE) {
		size_t received = recv(socket_fd, single_byte_buffer, 1, 0);
		if (received == 0) {
			if (DEBUG) {
				printf("[DEBUG:\n\tRECEIVED 0 BYTES FROM TCP\n]\n");
			}
			printf("CONNECTION LOST\n");
			exit(-1);
		}
		if (index_read >= 5 && command == NULL) {
			command = malloc(sizeof(char) * 6);
			strncpy(command, command_buffer, 5);
			command[5] = '\0';
		}
		BOOLEAN skip = FALSE;
		// n, m, h, w, s
		if (command != NULL) {
			#define FIRST_BYTE 6
			#define SECOND_BYTE FIRST_BYTE + 2
			int height_repr[2], width_repr[2];
			if (java_impl_equals(command, "WELCO")) {
				if (index_read == FIRST_BYTE || index_read == 14) {
					uint8_t party_id_or_ghosts = (uint8_t) single_byte_buffer[0];
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", party_id_or_ghosts);
					index_written += written;
					skip = TRUE;
				} else if (index_read == SECOND_BYTE) {
					height_repr[0] = (single_byte_buffer[0] % 256 + 256) % 256;
					skip = TRUE;
				} else if (index_read == SECOND_BYTE + 1) {
					height_repr[1] = (single_byte_buffer[0] % 256 + 256) % 256;
					uint32_t height = (height_repr[0]) | (height_repr[1] << 8);
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", height);
					index_written += written;
					skip = TRUE;
				}
				if (index_read == SECOND_BYTE + 3) {
					width_repr[0] = (single_byte_buffer[0] % 256 + 256) % 256;
					skip = TRUE;
				}
				if (index_read == SECOND_BYTE + 4) {
					width_repr[1] =  (single_byte_buffer[0] % 256 + 256) % 256;
					uint32_t width = (width_repr[0]) | (width_repr[1] << 8);
					size_t written = snprintf(command_buffer+index_written, DEFAULT_BUFFER_SIZE, "%d", width);
					index_written += written;
					skip = TRUE;
				}
			} else if (java_impl_equals(command, "LIST!")) {
				if (index_read == FIRST_BYTE || index_read == SECOND_BYTE) {
					uint8_t party_id_or_players = (uint8_t) single_byte_buffer[0];
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", party_id_or_players);
					index_written += written;
					skip = TRUE;
				}
			} else if (java_impl_equals(command, "SIZE!")) {
				if (index_read == FIRST_BYTE) {
					uint8_t party_id = (uint8_t) single_byte_buffer[0];
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", party_id);
					index_written += written;
					skip = TRUE;
				} else if (index_read == SECOND_BYTE) {
					height_repr[0] = (single_byte_buffer[0] % 256 + 256) % 256;
					skip = TRUE;
				} else if (index_read == SECOND_BYTE + 1) {
					height_repr[1] = (single_byte_buffer[0] % 256 + 256) % 256;
					uint32_t height = (height_repr[0]) | (height_repr[1] << 8);
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", height);
					index_written += written;
					skip = TRUE;
				} else if (index_read == SECOND_BYTE + 3) {
					width_repr[0] = (single_byte_buffer[0] % 256 + 256) % 256;
					skip = TRUE;
				} else if (index_read == SECOND_BYTE + 4) {
					width_repr[1] =  (single_byte_buffer[0] % 256 + 256) % 256;
					uint32_t width = (width_repr[0]) | (width_repr[1] << 8);
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", width);
					index_written += written;
					skip = TRUE;
				}
			} else if (java_impl_equals(command, "REGOK")) {
				if (index_read == FIRST_BYTE) {
					uint8_t party_id = (uint8_t) single_byte_buffer[0];
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", party_id);
					index_written += written;
					skip = TRUE;
				}
			} else if (java_impl_equals(command, "UNROK")) {
				if (index_read == FIRST_BYTE) {
					uint8_t party_id = (uint8_t) single_byte_buffer[0];
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", party_id);
					index_written += written;
					skip = TRUE;
				}
			} else if (java_impl_equals(command, "GAMES")) {
				if (index_read == FIRST_BYTE) {
					uint8_t games = (uint8_t) single_byte_buffer[0];
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", games);
					index_written += written;
					skip = TRUE;
				}
			} else if (java_impl_equals(command, "OGAME")) {
				if (index_read == FIRST_BYTE || index_read == SECOND_BYTE) {
					uint8_t party_id_or_players = (uint8_t) single_byte_buffer[0];
					size_t written = snprintf(command_buffer + index_written, DEFAULT_BUFFER_SIZE, "%d", party_id_or_players);
					index_written += written;
					skip = TRUE;
				}
			} else if (java_impl_equals(command, "GOBYE")) {
				in_game = FALSE;
			}
		}
		if (!skip)
			memcpy(command_buffer + index_written, single_byte_buffer, 1);
		if (index_read >= 7) {
			if (command_buffer[index_written] == '*' && command_buffer[index_written - 1] == '*' && command_buffer[index_written - 2] == '*') {
				command_buffer[index_written + 1] = '\0';
				command = NULL;
				return command_buffer;
			}
		}
		index_read++;
		if(!skip) index_written++;
	}
	return NULL;
}

void* tcp_read(void* arg) {
	int socket_fd = *((int*) arg);
	while (keep_going) {
		char* buffer = read_tcp_request(socket_fd);
		if (java_impl_equals(buffer, "!")) {
			perror("Connection lost");
			exit(-1);
		}
		if (DEBUG) {
			printf("[DEBUG:\n\tRECEIVED FROM SERVER:\n\t\"%s\"\n]\n", buffer);
		}
		java_type_splits* args = java_impl_split(buffer, " ");
		char* command = java_impl_substring_n(args->values[0], 0, 5);
		if (java_impl_equals(command, "WELCO")) {
			char* multicast_ip = args->values[5];
			size_t index_of_hashtag = java_impl_index_of(multicast_ip, "#");
			if (index_of_hashtag != -1) {
				multicast_ip = java_impl_substring_n(multicast_ip, 0, index_of_hashtag);
			}
			char* port_str = args->values[6];
			size_t port = atoi(port_str);
			udp_party_info party_info;
			party_info.party_net_address = multicast_ip;
			party_info.party_udp_port = port;
			in_game = TRUE;
			pthread_t udp_read0;
			pthread_create(&udp_read0, NULL, udp_read_party, &party_info);
			pthread_t udp_read1;
			pthread_create(&udp_read1, NULL, udp_read_players, &player_udp_port);
		}
		java_type_splits_free(args);
		printf("%s\n", buffer);	
	}
	return NULL;
}

/**
 * 
 * This should already be done & working correctly
 * 
 **/
int main(int argc, char** argv) {
	printf("WELCOME TO GHOSTLAB\n");
	int port = DEFAULT_PORT;
	if (argc == 2) {
		port = atoi(argv[1]);
		if (port <= 0) { printf("INVALID PORT: %s, FALLING BACK TO DEFAULT %d\n", argv[1], DEFAULT_PORT); port = DEFAULT_PORT; }
	}
	struct sockaddr_in socket_address;
	socket_address.sin_family = AF_INET;
	socket_address.sin_port = htons(port);
	inet_aton("127.0.0.1", &socket_address.sin_addr);
	printf("\nCONNECTING TO %s:%d\n", "127.0.0.1", port);
	int socket_fd = socket(PF_INET, SOCK_STREAM, 0);
	int ret_value = connect(socket_fd, (struct sockaddr *) &socket_address, sizeof(struct sockaddr_in));
	if (ret_value != -1) {
		printf("CONNECTED SUCCESSFULLY\n\n");
		keep_going = TRUE;
		in_game = FALSE;
		pthread_t thread_tcp_read;
		pthread_create(&thread_tcp_read, NULL, tcp_read, &socket_fd);
		while (keep_going) {
			char buffer[DEFAULT_BUFFER_SIZE];
			int in = read(STDIN_FILENO, buffer, DEFAULT_BUFFER_SIZE);
			buffer[in - 1] = '\0';
			if (in > 0) {
				int written = write(socket_fd, buffer, (in - 1) * sizeof(char));
				if (written == -1) {
					perror("Write failed");
					keep_going = FALSE;
					break;
				}
				if (!java_impl_equals(buffer, "")) {
					if (is_command_valid(buffer)) {
						if (DEBUG) {
							printf("[DEBUG:\n\tSENDING TO SERVER:\n\t\"%s\"\n]\n", buffer);
						}
						size_t terminator = java_impl_index_of(buffer, "***");
						char* request_stripped = java_impl_substring_n(buffer, 0, terminator);
						java_type_splits* args = java_impl_split(request_stripped, " ");
						char* command = args->values[0];
						if ((java_impl_equals(command, "NEWPL") || java_impl_equals(command, "REGIS")) && args->length >= 3) {
							player_udp_port = atoi(args->values[2]);
						}
						java_type_splits_free(args);
					} else {
						printf("INVALID COMMAND\n(USE COMMAND [HELP?***] TO SEE ALL AVAILABLE COMMANDS)\n");
					}
				} else {
					printf("USE COMMAND [HELP?***] TO SEE ALL AVAILABLE COMMANDS\n");
					fflush(stdout);
				}
			}
		}
	} else {
		perror("Error");
	}
	close(socket_fd);
	perror("Connection lost");
	return 0;
}
