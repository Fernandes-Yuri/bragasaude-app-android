declare module "@google-cloud/dataconnect" {
    export function validateAuthority(serviceId: string): void;
    export function getDataConnect(
        config: { serviceLocation: string },
        serviceResource: string
    ): {
        connector: any;
    };
}
