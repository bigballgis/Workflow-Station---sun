import React, { useEffect, useRef } from 'react';
import { io, Socket } from 'socket.io-client';
import { toast } from 'sonner';

import { authenticationSession } from '@/lib/authentication-session';
import { apHost } from '@/lib/host-config';

// HERMES L1 (#5): created lazily on first provider render (not at module load) so
// a host-injected socket origin/path applies. The embedding host proxies
// socket.io through its gateway (e.g. path '/api/ap/socket.io'); standalone AP
// falls back to same-origin '/api/socket.io'. test-run progress depends on this ws.
let socketSingleton: Socket | undefined;
function getSocket(): Socket {
  if (!socketSingleton) {
    socketSingleton = io(apHost.getSocketBaseUrl(), {
      transports: ['websocket'],
      path: apHost.getSocketPath(),
      autoConnect: false,
      reconnection: true,
    });
  }
  return socketSingleton;
}

const SocketContext = React.createContext<Socket>(undefined as unknown as Socket);

export const SocketProvider = ({ children }: { children: React.ReactNode }) => {
  const token = authenticationSession.getToken();
  const projectId = authenticationSession.getProjectId();
  const toastIdRef = useRef<string | null>(null);
  const socket = getSocket();

  useEffect(() => {
    if (token) {
      socket.auth = { token, projectId };
      if (!socket.connected) {
        socket.connect();

        socket.on('connect', () => {
          if (toastIdRef.current) {
            toast.dismiss(toastIdRef.current);
            toastIdRef.current = null;
          }
          console.log('connected to socket');
        });

        socket.on('disconnect', (reason) => {
          if (!toastIdRef.current) {
            const id = toast('Connection Lost', {
              id: 'websocket-disconnected',
              description: 'We are trying to reconnect...',
              duration: Infinity,
            });
            toastIdRef.current = id?.toString() ?? null;
          }
          if (reason === 'io server disconnect') {
            socket.connect();
          }
        });
      }
    } else {
      socket.disconnect();
    }
    return () => {
      socket.off('connect');
      socket.off('disconnect');
      socket.disconnect();
    };
  }, [token, projectId]);

  return (
    <SocketContext.Provider value={socket}>{children}</SocketContext.Provider>
  );
};

export const useSocket = () => React.useContext(SocketContext);
