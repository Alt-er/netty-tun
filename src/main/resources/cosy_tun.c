#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <sys/kern_control.h>
#include <sys/sys_domain.h>
#include <sys/un.h>
#include <net/if.h>
#include <net/if_utun.h>
#include <netinet/in.h>

#define UTUN_CONTROL_NAME "com.apple.net.utun_control"

struct tun_info
{
    int fd;
    int mtu;
    char device_name[IFNAMSIZ];
};

int create_tun(struct tun_info tun_info)
{
    // struct tun_info tun_info;
    // tun_info.mtu = 0;

    // printf("The address of a is %p\n", ptr1);
    // printf("The value of p is %p\n", ptr1);
    // for (int i = 0; i < sizeof(struct tun_info); i++)
    // {
    //     printf("%02x ", *ptr);
    //     ptr++;
    // }

    // 占位置
    // unsigned char *ptr1 = (unsigned char *)&tun_info;
    // unsigned char *ptr2 = (unsigned char *)&tun_info;
    // unsigned char *ptr3 = (unsigned char *)&tun_info;

    int mtu = tun_info.mtu;
    int fd, ctl_id, index = 0;
    struct ctl_info ctl_info;
    struct sockaddr_ctl address;
    struct ifreq ifreq;
    socklen_t sockNameLen;
    char sockName[IFNAMSIZ];

    // create socket
    fd = socket(PF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL);
    if (fd == -1)
    {
        perror("Create an endpoint for communication failed");
        exit(EXIT_FAILURE);
    }

    // mark socket as utun device
    memset(&ctl_info, 0, sizeof(ctl_info));
    strncpy(ctl_info.ctl_name, UTUN_CONTROL_NAME, sizeof(ctl_info.ctl_name) - 1);
    if (ioctl(fd, CTLIOCGINFO, &ctl_info) == -1)
    {
        perror("Failed to get info of kernel control");
        exit(EXIT_FAILURE);
    }

    // define address of socket
    memset(&address, 0, sizeof(address));
    address.sc_len = sizeof(address);
    address.sc_family = AF_SYSTEM;
    address.ss_sysaddr = AF_SYS_CONTROL;
    address.sc_id = ctl_info.ctl_id;
    address.sc_unit = index;

    if (connect(fd, (struct sockaddr *)&address, sizeof(address)) == -1)
    {
        perror("Failed to connect to the control socket");
        exit(EXIT_FAILURE);
    }
    // printf("\nUTUN device name: %s, MTU: %d\n", tun_info.device_name, tun_info.mtu);
    // get socket name
    memset(sockName, 0, sizeof(sockName));
    sockNameLen = sizeof(sockName);
    if (getsockopt(fd, SYSPROTO_CONTROL, UTUN_OPT_IFNAME, sockName, &sockNameLen) == -1)
    {
        perror("Failed to get socket name");
        exit(EXIT_FAILURE);
    }

    if (mtu != 0)
    {
        // set mtu
        // sscanf(argv[1], "%d", &mtu);
        memset(&ifreq, 0, sizeof(ifreq));
        strncpy(ifreq.ifr_name, sockName, IFNAMSIZ - 1);
        ifreq.ifr_mtu = mtu;
        if (ioctl(fd, SIOCSIFMTU, &ifreq) == -1)
        {
            perror("Failed to set MTU of utun interface");
            exit(EXIT_FAILURE);
        }
    }
    else
    {
        // get mtu
        memset(&ifreq, 0, sizeof(ifreq));
        strncpy(ifreq.ifr_name, sockName, IFNAMSIZ - 1);
        if (ioctl(fd, SIOCGIFMTU, &ifreq) == -1)
        {
            perror("Failed to get MTU of utun interface");
            exit(EXIT_FAILURE);
        }
        mtu = ifreq.ifr_mtu;
    }

    // sleep(60);
    tun_info.fd = fd;
    tun_info.mtu = mtu;
    strncpy(tun_info.device_name, sockName, IFNAMSIZ - 1);
    printf("UTUN device name: %s, MTU: %d\n", tun_info.device_name, tun_info.mtu);
    // close(fd);
    return 0;
}

// int main()
// {
//     struct tun_info tun_info;
//     tun_info.mtu = 0;
//     create_tun(tun_info);
//     return 0;
// }

// gcc -shared -fPIC -o libcosytun.dylib cosy_tun.c
// gcc -o cosy_tun cosy_tun.c &&  sudo ./cosy_tun