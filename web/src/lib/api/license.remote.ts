import { form, query, getRequestEvent } from '$app/server';
import { redirect } from '@sveltejs/kit';
import { db } from '$lib/server/db';
import { user } from '$lib/server/db/schema';
import { eq } from 'drizzle-orm';
import { env } from '$env/dynamic/private';
import { activateLicenseSchema, activateCheckoutSchema } from '$lib/schema/license';

export const getLicenseStatus = query(async () => {
	const { locals } = getRequestEvent();
	if (!locals.user) return null;

	const rows = await db
		.select({ plan: user.plan, polarLicenseKey: user.polarLicenseKey })
		.from(user)
		.where(eq(user.id, locals.user.id))
		.limit(1);

	const row = rows[0];
	return {
		activated: !!row?.plan,
		plan: row?.plan ?? null,
		licenseKey: row?.polarLicenseKey ?? null
	};
});

export const activateLicense = form(activateLicenseSchema, async (data) => {
	const { locals } = getRequestEvent();
	if (!locals.user) return;

	const serverUrl = env.SERVER_URL || 'http://localhost:8080';
	const internalSecret = env.INTERNAL_SECRET || '';

	const res = await fetch(`${serverUrl}/license/activate`, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json',
			'x-internal-secret': internalSecret,
			'x-internal-user-id': locals.user.id
		},
		body: JSON.stringify({ key: data.key })
	});

	if (res.ok) {
		redirect(303, '/dashboard');
	}
});

export const activateFromCheckout = form(activateCheckoutSchema, async (data) => {
	const { locals } = getRequestEvent();
	if (!locals.user) return;

	const serverUrl = env.SERVER_URL || 'http://localhost:8080';
	const internalSecret = env.INTERNAL_SECRET || '';

	const res = await fetch(`${serverUrl}/license/activate-checkout`, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json',
			'x-internal-secret': internalSecret,
			'x-internal-user-id': locals.user.id
		},
		body: JSON.stringify({ checkoutId: data.checkoutId })
	});

	if (res.ok) {
		redirect(303, '/dashboard');
	}
});
