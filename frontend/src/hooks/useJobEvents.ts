import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { BASE_URL } from '@/api/client';
import { Job } from '@/api/types';
import { getAuthHeaders } from '@/lib/auth';

export function useJobEvents(jobId: string | undefined) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!jobId) return;

    const controller = new AbortController();

    async function subscribe() {
      try {
        const response = await fetch(`${BASE_URL}/jobs/${jobId}/events`, {
          headers: {
            Accept: 'text/event-stream',
            ...getAuthHeaders(),
          },
          cache: 'no-store',
          signal: controller.signal,
        });

        if (!response.ok || !response.body) {
          throw new Error(`SSE request failed with status ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (!controller.signal.aborted) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');

          let boundary = buffer.indexOf('\n\n');
          while (boundary >= 0) {
            const block = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            boundary = buffer.indexOf('\n\n');

            const data = block
              .split('\n')
              .filter((line) => line.startsWith('data:'))
              .map((line) => line.slice(5).trimStart())
              .join('\n');

            if (!data) continue;
            const { jobId: eventJobId, status, timestamp } = JSON.parse(data);

            queryClient.setQueryData(['job', eventJobId], (oldData: Job | undefined) => {
              if (!oldData) return oldData;
              return { ...oldData, status, updatedAt: timestamp || new Date().toISOString() };
            });

            queryClient.invalidateQueries({ queryKey: ['jobs'] });
            queryClient.invalidateQueries({ queryKey: ['dashboard'] });
            queryClient.invalidateQueries({ queryKey: ['assets'] });

            if (status === 'COMPLETED' || status === 'FAILED') {
              controller.abort();
              return;
            }
          }
        }
      } catch (error) {
        if (!controller.signal.aborted) console.error('SSE Error:', error);
      }
    }

    subscribe();

    return () => {
      controller.abort();
    };
  }, [jobId, queryClient]);
}
