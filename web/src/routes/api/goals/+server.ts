import { json, error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import type { RequestHandler } from './$types';

const SERVER_URL = env.SERVER_URL || 'http://localhost:8080';

export const POST: RequestHandler = async ({ request, locals }) => {
	if (!locals.user) {
		return error(401, 'Unauthorized');
	}

	const body = await request.json();

	const res = await fetch(`${SERVER_URL}/goals`, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json',
			cookie: request.headers.get('cookie') ?? ''
		},
		body: JSON.stringify(body)
	});

	if (!res.ok) {
		const err = await res.json().catch(() => ({ error: 'Unknown error' }));
		return error(res.status, err.error ?? 'Failed to submit goal');
	}

	return json(await res.json());
};
