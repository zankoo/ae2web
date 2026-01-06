import { writable } from 'svelte/store';

export type ToastType = '' | 'success' | 'error' | 'info';

export type Toast = {
	id: number;
	message: string;
	type: ToastType;
};

const toasts = writable<Toast[]>([]);
let nextId = 1;

export const toastStore = {
	subscribe: toasts.subscribe
};

export function showToast(message: string, type: ToastType = ''): void {
	const toast: Toast = { id: nextId++, message, type };
	toasts.update((list) => [...list, toast]);

	setTimeout(() => {
		toasts.update((list) => list.filter((t) => t.id !== toast.id));
	}, 4000);
}
